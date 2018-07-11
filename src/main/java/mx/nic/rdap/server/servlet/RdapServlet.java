package mx.nic.rdap.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import mx.nic.rdap.core.db.Autnum;
import mx.nic.rdap.core.db.Domain;
import mx.nic.rdap.core.db.Entity;
import mx.nic.rdap.core.db.IpNetwork;
import mx.nic.rdap.core.db.Link;
import mx.nic.rdap.core.db.Nameserver;
import mx.nic.rdap.core.db.RdapObject;
import mx.nic.rdap.core.db.Remark;
import mx.nic.rdap.db.exception.RdapDataAccessException;
import mx.nic.rdap.db.exception.http.HttpException;
import mx.nic.rdap.renderer.Renderer;
import mx.nic.rdap.renderer.object.ExceptionResponse;
import mx.nic.rdap.renderer.object.HelpResponse;
import mx.nic.rdap.renderer.object.RdapResponse;
import mx.nic.rdap.renderer.object.RequestResponse;
import mx.nic.rdap.renderer.object.SearchResponse;
import mx.nic.rdap.server.configuration.RdapConfiguration;
import mx.nic.rdap.server.notices.UserNotices;
import mx.nic.rdap.server.privacy.AutnumPrivacyFilter;
import mx.nic.rdap.server.privacy.DomainPrivacyFilter;
import mx.nic.rdap.server.privacy.EntityPrivacyFilter;
import mx.nic.rdap.server.privacy.IpNetworkPrivacyFilter;
import mx.nic.rdap.server.privacy.NameserverPrivacyFilter;
import mx.nic.rdap.server.renderer.RendererPool;
import mx.nic.rdap.server.renderer.RendererWrapper;
import mx.nic.rdap.server.result.NameserverResult;
import mx.nic.rdap.server.result.RdapResult;
import mx.nic.rdap.server.servlet.AcceptHeaderFieldParser.Accept;
import mx.nic.rdap.server.util.PrivacyUtil;

/**
 * Base class of all RDAP servlets.
 */
public abstract class RdapServlet extends HttpServlet {

	/** This is just a warning shutupper. */
	private static final long serialVersionUID = 1L;

	private final static Logger logger = Logger.getLogger(RdapServlet.class.getName());

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		RdapResult result;

		try {
			result = doRdapGet(request);
		} catch (HttpException e) {
			response.sendError(e.getHttpResponseStatusCode(), e.getMessage());
			return;
		} catch (RdapDataAccessException e) {
			// Handled as an "Internal Server Error", it probably has some good things to
			// log
			logger.log(Level.SEVERE, e.getMessage(), e);
			response.sendError(500, e.getMessage());
			return;
		}

		if (result == null) {
			response.sendError(404);
			return;
		}

		RendererWrapper renderer = findRenderer(request);
		response.setCharacterEncoding("UTF-8");

		response.setContentType(renderer.getMimeType());
		// Recommendation of RFC 7480 section 5.6
		response.setHeader("Access-Control-Allow-Origin", "*");
		renderResult(renderer.getRenderer(), result, response.getWriter());
	}

	@SuppressWarnings("unchecked")
	private void renderResult(Renderer renderer, RdapResult result, PrintWriter printWriter) {
		// Set the language
		if (result.getRdapResponse() instanceof RequestResponse) {
			RequestResponse<RdapObject> requestResponse = (RequestResponse<RdapObject>) result.getRdapResponse();
			requestResponse.getRdapObject().setLang(RdapConfiguration.getServerLanguage());
		} else if (result.getRdapResponse() instanceof SearchResponse) {
			SearchResponse<RdapObject> searchResponse = (SearchResponse<RdapObject>) result.getRdapResponse();
			searchResponse.getRdapObjects()
					.forEach(rdapObject -> rdapObject.setLang(RdapConfiguration.getServerLanguage()));
		}

		// Add nsSharingNameConformance
		if (RdapConfiguration.isNameserverSharingNameConformance()) {
			if (result.getRdapResponse().getRdapConformance() == null) {
				result.getRdapResponse().setRdapConformance(new ArrayList<>());
			}

			result.getRdapResponse().getRdapConformance().add("rdap_nameservers_sharing_name");
		}

		// Add TOS notice if exists
		List<Remark> tos = UserNotices.getTos();
		if (tos != null && !tos.isEmpty()) {
			RdapResponse response = result.getRdapResponse();
			if (response.getNotices() == null) {
				response.setNotices(new ArrayList<>());
			}
			response.getNotices().addAll(tos);
		}

		List<Remark> userNotices = UserNotices.getNotices();
		if (userNotices != null && !userNotices.isEmpty()) {
			RdapResponse response = result.getRdapResponse();
			if (response.getNotices() == null) {
				response.setNotices(new ArrayList<>());
			}
			response.getNotices().addAll(userNotices);
		}

		// Filter objects according to privacy settings
		boolean wasFiltered = false;
		switch (result.getResultType()) {
		case AUTNUM:
			RequestResponse<Autnum> autnumRequestResponse = (RequestResponse<Autnum>) result.getRdapResponse();
			wasFiltered = AutnumPrivacyFilter.filterAutnum(autnumRequestResponse.getRdapObject());
			if (wasFiltered) {
				PrivacyUtil.addPrivacyRemarkAndStatus(autnumRequestResponse.getRdapObject());
			}
			renderer.renderAutnum(autnumRequestResponse, printWriter);
			break;
		case DOMAIN:
			RequestResponse<Domain> domainRequestResponse = (RequestResponse<Domain>) result.getRdapResponse();
			wasFiltered = DomainPrivacyFilter.filterDomain(domainRequestResponse.getRdapObject());
			if (wasFiltered) {
				PrivacyUtil.addPrivacyRemarkAndStatus(domainRequestResponse.getRdapObject());
			}
			renderer.renderDomain(domainRequestResponse, printWriter);
			break;
		case DOMAINS:
			SearchResponse<Domain> domainSearchResponse = (SearchResponse<Domain>) result.getRdapResponse();
			for (Domain domain : domainSearchResponse.getRdapObjects()) {
				wasFiltered = DomainPrivacyFilter.filterDomain(domain);
				if (wasFiltered) {
					PrivacyUtil.addPrivacyRemarkAndStatus(domain);
				}
			}
			renderer.renderDomains(domainSearchResponse, printWriter);
			break;
		case ENTITIES:
			SearchResponse<Entity> entitySearchResponse = (SearchResponse<Entity>) result.getRdapResponse();
			for (Entity entity : entitySearchResponse.getRdapObjects()) {
				wasFiltered = EntityPrivacyFilter.filterEntity(entity);
				if (wasFiltered) {
					PrivacyUtil.addPrivacyRemarkAndStatus(entity);
				}
			}
			renderer.renderEntities(entitySearchResponse, printWriter);
			break;
		case ENTITY:
			RequestResponse<Entity> entityRequestResponse = (RequestResponse<Entity>) result.getRdapResponse();
			wasFiltered = EntityPrivacyFilter.filterEntity(entityRequestResponse.getRdapObject());
			if (wasFiltered) {
				PrivacyUtil.addPrivacyRemarkAndStatus(entityRequestResponse.getRdapObject());
			}
			renderer.renderEntity(entityRequestResponse, printWriter);
			break;
		case EXCEPTION:
			renderer.renderException((ExceptionResponse) result.getRdapResponse(), printWriter);
			break;
		case HELP:
			renderer.renderHelp((HelpResponse) result.getRdapResponse(), printWriter);
			break;
		case IP:
			RequestResponse<IpNetwork> ipRequestResponse = (RequestResponse<IpNetwork>) result.getRdapResponse();
			wasFiltered = IpNetworkPrivacyFilter.filterIpNetwork(ipRequestResponse.getRdapObject());
			if (wasFiltered) {
				PrivacyUtil.addPrivacyRemarkAndStatus(ipRequestResponse.getRdapObject());
			}
			renderer.renderIpNetwork(ipRequestResponse, printWriter);
			break;
		case NAMESERVER:
			RequestResponse<Nameserver> nameserverRequestResponse = (RequestResponse<Nameserver>) result
					.getRdapResponse();
			wasFiltered = NameserverPrivacyFilter.filterNameserver(nameserverRequestResponse.getRdapObject());
			if (wasFiltered) {
				PrivacyUtil.addPrivacyRemarkAndStatus(nameserverRequestResponse.getRdapObject());
			}
			handleNameserver(result, nameserverRequestResponse.getRdapObject());
			renderer.renderNameserver(nameserverRequestResponse, printWriter);
			break;
		case NAMESERVERS:
			SearchResponse<Nameserver> nameserverSearchResponse = (SearchResponse<Nameserver>) result.getRdapResponse();
			for (Nameserver nameserver : nameserverSearchResponse.getRdapObjects()) {
				wasFiltered = NameserverPrivacyFilter.filterNameserver(nameserver);
				if (wasFiltered) {
					PrivacyUtil.addPrivacyRemarkAndStatus(nameserver);
				}
			}
			renderer.renderNameservers(nameserverSearchResponse, printWriter);
			break;
		default:
			break;
		}

	}

	private void handleNameserver(RdapResult result, Nameserver ns) {
		if (!(result instanceof NameserverResult)) {
			return;
		}

		if (!RdapConfiguration.isNameserverSharingNameConformance()) {
			return;
		}

		NameserverResult nsResult = (NameserverResult) result;

		Link searchOtherNS = nsResult.getSearchOtherNS();
		if (searchOtherNS == null) {
			return;
		}

		if (ns.getLinks() == null) {
			ns.setLinks(new ArrayList<>());
		}

		ns.getLinks().add(searchOtherNS);
	}

	/**
	 * Handles the `request` GET request and builds a response. Think of it as a
	 * {@link HttpServlet#doGet(HttpServletRequest, HttpServletResponse)}, except
	 * the response will be built for you.
	 * 
	 * @param request    request to the servlet.
	 * @param connection Already initialized connection to the database.
	 * @return response to the user.
	 * @throws HttpException Errors found handling `request`.
	 */
	protected abstract RdapResult doRdapGet(HttpServletRequest request) throws HttpException, RdapDataAccessException;

	/**
	 * Tries hard to find the best suitable renderer for <code>httpRequest</code>.
	 */
	private RendererWrapper findRenderer(HttpServletRequest httpRequest) {
		RendererWrapper renderer;

		AcceptHeaderFieldParser parser = new AcceptHeaderFieldParser(httpRequest.getHeader("Accept"));
		PriorityQueue<Accept> accepts = parser.getQueue();

		while (!accepts.isEmpty()) {
			renderer = RendererPool.get(accepts.remove().getMediaRange());
			if (renderer != null) {
				return renderer;
			}
		}

		return RendererPool.getDefaultRenderer();
	}

}
