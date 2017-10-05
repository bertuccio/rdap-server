package mx.nic.rdap.server.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;

import mx.nic.rdap.core.catalog.RemarkType;
import mx.nic.rdap.core.catalog.Status;
import mx.nic.rdap.core.db.Remark;
import mx.nic.rdap.server.catalog.PrivacyStatus;
import mx.nic.rdap.server.configuration.RdapConfiguration;
import mx.nic.rdap.server.listener.RdapInitializer;

public class PrivacyUtil {

	private static Map<String, Map<String, String>> OBJECTS_PRIVACY_SETTING = new HashMap<>();

	// ***** Names of the properties files *****
	public static final String ENTITY = "entity";
	public static final String ENTITY_PUBLIC_ID = "entity_public_id";
	public static final String ENTITY_LINKS = "entity_links";
	public static final String ENTITY_REMARKS = "entity_remarks";
	public static final String ENTITY_EVENTS = "entity_events";

	public static final String VCARD = "vcard";

	public static final String DOMAIN = "domain";
	public static final String DOMAIN_PUBLIC_ID = "domain_public_id";
	public static final String DOMAIN_VARIANTS = "domain_variants";
	public static final String DOMAIN_LINKS = "domain_links";
	public static final String DOMAIN_REMARKS = "domain_remarks";
	public static final String DOMAIN_EVENTS = "domain_events";
	public static final String SECURE_DNS = "secure_dns";
	public static final String DS_DATA = "ds_data";
	public static final String KEY_DATA = "key_data";

	public static final String NAMESERVER = "nameserver";
	public static final String NAMESERVER_LINKS = "nameserver_links";
	public static final String NAMESERVER_REMARKS = "nameserver_remarks";
	public static final String NAMESERVER_EVENTS = "nameserver_events";

	public static final String AUTNUM = "autnum";
	public static final String AUTNUM_LINKS = "autnum_links";
	public static final String AUTNUM_REMARKS = "autnum_remarks";
	public static final String AUTNUM_EVENTS = "autnum_events";

	public static final String IP_NETWORK = "ip_network";
	public static final String IP_NETWORK_LINKS = "ip_network_links";
	public static final String IP_NETWORK_REMARKS = "ip_network_remarks";
	public static final String IP_NETWORK_EVENTS = "ip_network_events";

	// ***** End of names of the properties files *****

	/** Path where the default properties are read */
	private static final String DEFAULT_PATH = "META-INF/privacy_default/";

	/** Path where the user properties are read */
	private static final String USER_PATH = "WEB-INF/privacy/";


	public static void loadAllPrivacySettings() throws IOException {
		loadObjectPrivacySettings(ENTITY);
		loadObjectPrivacySettings(ENTITY_PUBLIC_ID);
		loadObjectPrivacySettings(ENTITY_LINKS);
		loadObjectPrivacySettings(ENTITY_EVENTS);
		loadObjectPrivacySettings(ENTITY_REMARKS);

		loadObjectPrivacySettings(VCARD);

		loadObjectPrivacySettings(DOMAIN);
		loadObjectPrivacySettings(DOMAIN_PUBLIC_ID);
		loadObjectPrivacySettings(DOMAIN_VARIANTS);
		loadObjectPrivacySettings(DOMAIN_LINKS);
		loadObjectPrivacySettings(DOMAIN_EVENTS);
		loadObjectPrivacySettings(DOMAIN_REMARKS);
		loadObjectPrivacySettings(SECURE_DNS);
		loadObjectPrivacySettings(DS_DATA);
		loadObjectPrivacySettings(KEY_DATA);

		loadObjectPrivacySettings(NAMESERVER);
		loadObjectPrivacySettings(NAMESERVER_LINKS);
		loadObjectPrivacySettings(NAMESERVER_EVENTS);
		loadObjectPrivacySettings(NAMESERVER_REMARKS);

		loadObjectPrivacySettings(AUTNUM);
		loadObjectPrivacySettings(AUTNUM_LINKS);
		loadObjectPrivacySettings(AUTNUM_EVENTS);
		loadObjectPrivacySettings(AUTNUM_REMARKS);

		loadObjectPrivacySettings(IP_NETWORK);
		loadObjectPrivacySettings(IP_NETWORK_LINKS);
		loadObjectPrivacySettings(IP_NETWORK_EVENTS);
		loadObjectPrivacySettings(IP_NETWORK_REMARKS);

	}
	
	private static void loadUserPrivacySettings(String fileName, Properties properties) throws IOException {
		ServletContext ctxt = RdapInitializer.getServletContext();
		Path path = null;
		InputStream inStream = null;
		if (ctxt != null) {
			String initParameter = ctxt.getInitParameter(RdapInitializer.PRIVACY_SETTINGS_PARAM_NAME);
			if (initParameter == null) {
				path = Paths.get(USER_PATH, fileName + ".properties");
				inStream = ctxt.getResourceAsStream(path.toString());
			} else {
				path = Paths.get(initParameter, fileName + ".properties");
				inStream = ctxt.getResourceAsStream(path.toString());
			}
		} else {
			path = Paths.get("META-INF/privacy/", fileName + ".properties");
			inStream = PrivacyUtil.class.getClassLoader().getResourceAsStream(path.toString());
		}

		if (inStream != null) {
			try {
				properties.load(inStream);
			} catch (Exception e) {
				throw new IOException("Cannot load file: " + path.toString(), e);
			} finally {
				inStream.close();
			}
		}
	}

	private static void loadObjectPrivacySettings(String objectName) throws IOException {
		Properties properties = new Properties();
		ClassLoader classLoader = PrivacyUtil.class.getClassLoader();
		HashMap<String, String> objectProperties = new HashMap<>();
		try (InputStream in = classLoader.getResourceAsStream(DEFAULT_PATH + objectName + ".properties");) {
			properties.load(in);
		} catch (NullPointerException e) {
			throw new IOException("Cannot load file: " + DEFAULT_PATH + objectName + ".properties", e);
		}

		loadUserPrivacySettings(objectName, properties);

		StringBuilder builder = new StringBuilder();
		boolean isInvalidProperties = false;
		Set<Object> keySet = properties.keySet();
		for (Object keyObj : keySet) {
			String key = (String) keyObj;
			// There is no empty value.
			if (key.isEmpty()) {
				continue;
			}

			String property = properties.getProperty(key).trim();
			if (!property.contains(",")) {
				try {
					PrivacyStatus privacyProperty = PrivacyStatus.valueOf(property.toUpperCase());
					objectProperties.put(key, privacyProperty.toString().toLowerCase());
				} catch (IllegalArgumentException e) {
					// Can be a custom role, must be configured
					if (RdapConfiguration.isUserRoleConfigured(property)) {
						objectProperties.put(key, property.toLowerCase());
					} else {
						isInvalidProperties = true;
						builder.append(key).append("=").append(property).append(" (unknown value); ");
					}
				}
			} else {
				// List of roles, only "OWNER" can be mixed with custom roles
				String[] privacyRoles = property.split(",");
				StringBuilder errorsList = new StringBuilder(key + "=");
				boolean localError = false;
				for (String privacyRole : privacyRoles) {
					privacyRole = privacyRole.trim();
					try {
						PrivacyStatus privacyStatus = PrivacyStatus.valueOf(privacyRole.toUpperCase());
						if (!privacyStatus.equals(PrivacyStatus.OWNER)) {
							localError = true;
							errorsList.append(privacyRole).append(" (can't be mixed with custom roles), ");
						} else {
							objectProperties.put(key, privacyStatus.toString().toLowerCase());
						}
					} catch (IllegalArgumentException e) {
						// Can be a custom role, must be configured
						if (RdapConfiguration.isUserRoleConfigured(privacyRole)) {
							objectProperties.put(key, privacyRole.toLowerCase());
						} else {
							localError = true;
							errorsList.append(privacyRole).append(" (unknown value), ");
						}
					}
				}
				if (localError) {
					isInvalidProperties = true;
					builder.append(errorsList.substring(0, errorsList.toString().length() - 2)).append("; ");
				}
			}
		}

		if (isInvalidProperties) {
			throw new RuntimeException(
					"Invalid privacy file '" + objectName + ".properties'.\n Invalid values: " + builder.substring(0, builder.toString().length() - 2));
		}

		OBJECTS_PRIVACY_SETTING.put(objectName, Collections.unmodifiableMap(objectProperties));
	}

	/**
	 * Indicate if the object or attribute should be show to the user according to
	 * the privacy status of the object or attribute.
	 * 
	 * @param objectValue
	 *            Actual value of the object to be evaluate.
	 * @param objectName
	 *            Name of the object or attribute.
	 * @param objectPrivacyStatus
	 *            Privacy status of the object.
	 * @param isAuthenticated
	 *            Indicate if the user is authenticated.
	 * @param isOwner
	 *            Indicate if the user is the owner of the object or attribute to
	 *            evaluate.
	 * @return <code>false</code> if the object or attribute shouldn't be shown
	 *         according of its privacy's level, or if the <b>objectValue</b> is
	 *         <code>null</code> or empty, otherwise <code>true</code>.
	 */
	@SuppressWarnings("rawtypes")
	public static boolean isObjectVisible(Object objectValue, String objectName, PrivacyStatus objectPrivacyStatus,
			boolean isAuthenticated, boolean isOwner) {
		if (objectValue == null)
			return false;

		if (objectValue instanceof String) {
			if (((String) objectValue).isEmpty()) {
				return false;
			}
		} else if (objectValue instanceof List && ((List) objectValue).isEmpty()) {
			return false;
		}

		if (objectPrivacyStatus == null) {
			throw new NullPointerException("Attribute '" + objectName + "' does not have privacy status configured.");
		}

		boolean result = false;
		switch (objectPrivacyStatus) {
		case OWNER:
			if (isOwner) {
				result = true;
			}
			break;
		case AUTHENTICATED:
			if (isAuthenticated) {
				result = true;
			}
			break;
		case ANY:
			result = true;
			break;
		case NONE:
			break;
		}
		return result;
	}

	public static Map<String, String> getEntityPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(ENTITY);
	}

	public static Map<String, String> getEntityPublicIdsPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(ENTITY_PUBLIC_ID);
	}

	public static Map<String, String> getNameserverPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(NAMESERVER);
	}

	public static Map<String, String> getDomainPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DOMAIN);
	}

	public static Map<String, String> getDomainPublicIdsPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DOMAIN_PUBLIC_ID);
	}

	public static Map<String, String> getDomainVariantsPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DOMAIN_VARIANTS);
	}

	public static Map<String, String> getSecureDnsPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(SECURE_DNS);
	}

	public static Map<String, String> getDsDataPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DS_DATA);
	}

	public static Map<String, String> getKeyDataPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(KEY_DATA);
	}

	public static Map<String, String> getEntityLinkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(ENTITY_LINKS);
	}

	public static Map<String, String> getEntityRemarkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(ENTITY_REMARKS);
	}

	public static Map<String, String> getEntityEventPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(ENTITY_EVENTS);
	}

	public static Map<String, String> getDomainLinkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DOMAIN_LINKS);
	}

	public static Map<String, String> getDomainRemarkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DOMAIN_REMARKS);
	}

	public static Map<String, String> getDomainEventPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(DOMAIN_EVENTS);
	}

	public static Map<String, String> getNameserverLinkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(NAMESERVER_LINKS);
	}

	public static Map<String, String> getNameserverRemarkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(NAMESERVER_REMARKS);
	}

	public static Map<String, String> getNameserverEventPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(NAMESERVER_EVENTS);
	}

	public static Map<String, String> getAutnumPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(AUTNUM);
	}

	public static Map<String, String> getAutnumLinkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(AUTNUM_LINKS);
	}

	public static Map<String, String> getAutnumRemarkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(AUTNUM_REMARKS);
	}

	public static Map<String, String> getAutnumEventPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(AUTNUM_EVENTS);
	}

	public static Map<String, String> getIpNetworkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(IP_NETWORK);
	}

	public static Map<String, String> getIpNetworkLinkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(IP_NETWORK_LINKS);
	}

	public static Map<String, String> getIpNetworkRemarkPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(IP_NETWORK_REMARKS);
	}

	public static Map<String, String> getIpNetworkEventPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(IP_NETWORK_EVENTS);
	}

	public static Map<String, String> getVCardPrivacySettings() {
		return OBJECTS_PRIVACY_SETTING.get(VCARD);
	}

	/**
	 * Return the privacy status with most priority.something like:
	 * none>owner>authenticate>any
	 */
	public static PrivacyStatus getPriorityPrivacyStatus(boolean isAuthenticated, boolean isOwner,
			Map<String, PrivacyStatus> privacySettings) {
		// First check if all the privacys settings are in "Any"
		if (!privacySettings.containsValue(PrivacyStatus.AUTHENTICATED)
				&& !privacySettings.containsValue(PrivacyStatus.OWNER)
				&& !privacySettings.containsValue(PrivacyStatus.NONE)) {
			return PrivacyStatus.ANY;
		} // Then, validate if all the privacy is
		else if (privacySettings.containsValue(PrivacyStatus.NONE)) {
			return PrivacyStatus.NONE;
		} else if (privacySettings.containsValue(PrivacyStatus.OWNER) && !isOwner) {
			return PrivacyStatus.OWNER;
		} else if (privacySettings.containsValue(PrivacyStatus.AUTHENTICATED) && !isAuthenticated) {
			return PrivacyStatus.AUTHENTICATED;
		} else
			return PrivacyStatus.ANY;
	}

	public static Status getObjectStatusFromPrivacy(boolean isAuthenticated, boolean isOwner,
			PrivacyStatus priorityStatus) {
		if (priorityStatus.equals(PrivacyStatus.ANY)) {
			return null;
		} else if (priorityStatus.equals(PrivacyStatus.NONE)) {
			return Status.REMOVED;
		} else if (priorityStatus.equals(PrivacyStatus.OWNER)) {
			return Status.PRIVATE;
		} else if (priorityStatus.equals(PrivacyStatus.AUTHENTICATED)) {
			return Status.PRIVATE;
		} else
			return null;
	}

	public static Remark getObjectRemarkFromPrivacy(boolean isAuthenticated, boolean isOwner,
			PrivacyStatus priorityStatus) {
		if (priorityStatus.equals(PrivacyStatus.ANY)) {
			return null;
		} else if (priorityStatus.equals(PrivacyStatus.NONE)) {
			return new Remark(RemarkType.OBJECT_AUTHORIZATION);
		} else if (priorityStatus.equals(PrivacyStatus.OWNER)) {
			return new Remark(RemarkType.OBJECT_AUTHORIZATION);
		} else if (priorityStatus.equals(PrivacyStatus.AUTHENTICATED)) {
			return new Remark(RemarkType.OBJECT_AUTHORIZATION);
		} else
			return new Remark(RemarkType.OBJECT_UNEXPLAINABLE);
	}
}
