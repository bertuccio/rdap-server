package mx.nic.rdap.server.servlet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.SecurityUtils;

import mx.nic.rdap.core.db.Entity;
import mx.nic.rdap.db.exception.RdapDataAccessException;
import mx.nic.rdap.db.exception.http.HttpException;
import mx.nic.rdap.db.service.DataAccessService;
import mx.nic.rdap.db.spi.EntityDAO;
import mx.nic.rdap.db.struct.SearchResultStruct;
import mx.nic.rdap.server.configuration.RdapConfiguration;
import mx.nic.rdap.server.result.EntitySearchResult;
import mx.nic.rdap.server.result.RdapResult;
import mx.nic.rdap.server.util.Util;

@WebServlet(name = 	"entities", urlPatterns = { "/entities" })
public class EntitySearchServlet extends DataAccessServlet<EntityDAO> {

	private static final long serialVersionUID = -8023237096799052268L;

	public static final String FULL_NAME = "fn";
	public static final String HANDLE = "handle";
	
	@Override
	protected EntityDAO initAccessDAO() throws RdapDataAccessException {
		return DataAccessService.getEntityDAO();
	}
	
	@Override
	protected String getServedObjectName() {
		return "entities";
	}

	@Override
	protected RdapResult doRdapDaGet(HttpServletRequest httpRequest, EntityDAO dao)
			throws HttpException, RdapDataAccessException {
		RdapSearchRequest searchRequest = RdapSearchRequest.getSearchRequest(httpRequest, true, false, FULL_NAME, HANDLE);

		String username = Util.getUsername(SecurityUtils.getSubject());
		SearchResultStruct<Entity> result = null;
		switch (searchRequest.getType()) {
		case PARTIAL_SEARCH:
			result = getPartialSearch(username, searchRequest, dao);
			break;
		case REGEX_SEARCH:
			result = getRegexSearch(username, searchRequest, dao);
			break;
		default:
			throw new RuntimeException("Programming error: Automatically-initialized field has an invalid value.");
		}

		if (result == null) {
			return null;
		}

		return new EntitySearchResult(Util.getServerUrl(httpRequest), httpRequest.getContextPath(), result, username);
	}

	private SearchResultStruct<Entity> getPartialSearch(String username, RdapSearchRequest searchRequest, EntityDAO dao)
			throws RdapDataAccessException {
		SearchResultStruct<Entity> result = null;

		int resultLimit = RdapConfiguration.getMaxNumberOfResultsForUser(username);
		switch (searchRequest.getParameterName()) {
		case FULL_NAME:
			result = dao.searchByVCardName(searchRequest.getParameterValue(), resultLimit);
			break;
		case HANDLE:
			result = dao.searchByHandle(searchRequest.getParameterValue(), resultLimit);
			break;
		default:
			throw new RuntimeException("Programming error: Automatically-initialized field has an invalid value.");
		}

		if (result != null) {
			result.truncate(resultLimit);
		}

		return result;
	}

	private SearchResultStruct<Entity> getRegexSearch(String username, RdapSearchRequest searchRequest, EntityDAO dao)
			throws RdapDataAccessException {
		SearchResultStruct<Entity> result = null;

		int resultLimit = RdapConfiguration.getMaxNumberOfResultsForUser(username);
		switch (searchRequest.getParameterName()) {
		case FULL_NAME:
			result = dao.searchByRegexVCardName(searchRequest.getParameterValue(), resultLimit);
			break;
		case HANDLE:
			result = dao.searchByRegexHandle(searchRequest.getParameterValue(), resultLimit);
			break;
		default:
			throw new RuntimeException("Programming error: Automatically-initialized field has an invalid value.");
		}
		
		if (result != null) {
			result.truncate(resultLimit);
		}

		return result;
	}

}
