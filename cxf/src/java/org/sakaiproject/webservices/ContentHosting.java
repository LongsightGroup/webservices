/**
 * Copyright (c) 2005 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.webservices;

import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import lombok.extern.slf4j.Slf4j;


/**
 *	The ContentHosting class services acts as a web service end point for methods that provide access to the
 *	Sakai Content Hosting Service.  Use SakaiLogin.jws to establish a user session.  A method is provided
 *	to get the root collection id for a given site id, from there, collections can be recursively opened
 *	and examined for resources.
 *
 *	TODO:  Add better logging entries and clean up return values so they are standardised.
 *
 *	@author Mark J. Norton <a href="mailto:markjnorton@earthlink.net">
 *	@author David Horwitz <a href="dhorwitz@ched.uct.ac.za">
  *	@author Steve Swinsburg <a href="s.swinsburg@lancaster.ac.uk">
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
@Slf4j
public class ContentHosting extends AbstractWebService {

	private static final String VIRTUAL_ROOT_ID = "Virtual-Root-Identifier";
	private static final String VIRTUAL_ROOT_NAME = "Federated Collections";
	private static final String RESOURCE_TYPE_COLLECTION = "collection";
	private static final String RESOURCE_TYPE_RESOURCE = "resource";
	private static final String RESOURCE_TYPE_ATTACHMENT = "attachment";
	private static Base64 base64 = new Base64();
	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
	
	/**
	 *	Get the collection id for the root collection associated with this site context.
	 *
	 *	@param context the site context
	 *	@return a root collection id string
	 *
	 *	@author Dave
	 */
	private String getCollectionId (String context) {
		
 		return	contentHostingService.getSiteCollection(context);
	}


	/**
	 *	Get the virtual root id.  The virtual root serves as a container for all of the 
	 *	CHS root collections that the user currently has access to.
	 *
	 *	@param a valid session id.
	 *
	 *	@author Mark Norton
	 */
    @WebMethod
    @Path("/getVirtualRoot")
    @Produces("text/plain")
    @GET
    public String getVirtualRoot(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid) {
        Session session = establishSession(sessionid);
		return ContentHosting.VIRTUAL_ROOT_ID;
	}
	

	/**
	 *	Get the size of the root collection (total content size on the site). 
	 *
	 *	@param sessionid a valid sessionid
	 *	@param context the site context
	 *	@return the size of the collection in thousands of bytes or -1 if failure.
	 *
	 *	@author David Horwitz
	 */
    @WebMethod
    @Path("/getSiteCollectionSize")
    @Produces("text/plain")
    @GET
    public long getSiteCollectionSize(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "context", partName = "context") @QueryParam("context") String context) {
        try {
			//establish the session
			Session s = establishSession(sessionid);
			String collectionId = getCollectionId(context);
			ContentCollection collection = contentHostingService.getCollection(collectionId);
			return collection.getBodySizeK();
		}
		catch (Exception e) {
			log.error("getSiteCollectionSize(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return -1;
	}
	
	/**
	 *	Get content information about a site.
	 *
	 *	@param sessionid a valid sessionid
	 *	@return an XML document with site elements and attributes about each
	 *
	 *	@author David Horwitz
	 *	@author Steve Swinsburg
	 *
	 * TODO - do we want element/attributes as the XML or nodes/childnodes like the rest of the web services?
	 *
	 */
    @WebMethod
    @Path("/getAllSitesCollectionSize")
    @Produces("text/plain")
    @GET
    public String getAllSitesCollectionSize(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid) {
        try {
		
			Session s = establishSession(sessionid);
			List sites = siteService.getSites(org.sakaiproject.site.api.SiteService.SelectionType.ANY, null, null, null, SortType.TITLE_ASC, null);
			
			Document dom = Xml.createDocument();
			Node list = dom.createElement("list");
			dom.appendChild(list);
			
			for (int i = 0; i <sites.size();i++){
				Site site = (Site)sites.get(i);
				Long size = getSiteCollectionSize(sessionid,site.getId());
				Element siteNode = dom.createElement("site");
				siteNode.setAttribute("id", site.getId());
				siteNode.setAttribute("title", site.getTitle());
				siteNode.setAttribute("size", size.toString());
				siteNode.setAttribute("type", site.getType());
				siteNode.setAttribute("createdBy", site.getCreatedBy().getEid());
				Date time = site.getCreatedDate();
				DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
				siteNode.setAttribute("createdTime", df.format(time));
	        		
				list.appendChild(siteNode);
			}
			
			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e) {
			log.error("getAllSitesCollectionSize(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return "failure";
	}


	/**
	 *	Create a root collection for a site given it's context.
	 *
	 *	TODO:  Add a message notifying the Resource tool that a root.has been added to a site.
	 *
	 *	@param sessionid a valid sessionid
	 *	@return the id of the new collection.
	 *
	 *	@author David Horwitz
	 */
    @WebMethod
    @Path("/createTopFolder")
    @Produces("text/plain")
    @GET
    public String createTopFolder(
            @WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
            @WebParam(name = "context", partName = "context") @QueryParam("context") String context,
            @WebParam(name = "name", partName = "name") @QueryParam("name") String name) {
        String collectionId = getCollectionId(context);
		return createFolder(sessionId, collectionId, name);
	}

	/**
	 *	Create a collection for a site given it's id (with path).
	 *
	 *	TODO:  Add a message notifying the Resource tool that a collection.has been created.
	 *
	 *	@param sessionid a valid sessionid
	 *	@return the id of the new collection.
	 *
	 *	@author David Horwitz
	 */
    @WebMethod
    @Path("/createFolder")
    @Produces("text/plain")
    @GET
    public String createFolder(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "collectionId", partName = "collectionId") @QueryParam("collectionId") String collectionId,
            @WebParam(name = "name", partName = "name") @QueryParam("name") String name) {

        try {
			//establish the session
			Session s = establishSession(sessionid);

			String newCollectionId = collectionId + name + Entity.SEPARATOR;
			
			ResourcePropertiesEdit resourceProperties = contentHostingService.newResourceProperties();
			
			resourceProperties.addProperty (ResourceProperties.PROP_DISPLAY_NAME, name);
			//resourceProperties.addProperty (ResourceProperties.PROP_DESCRIPTION, description);
			
			ContentCollection collection = contentHostingService.addCollection(newCollectionId, resourceProperties);
			
			return newCollectionId;
		}
		catch (Exception e) {
			log.error("createFolder(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return "failure";
	}

	/**
	 *	Add a resource to a given collection.  The resource is passed either as text or encoded using Base64 flagged
	 *	using the binary parameter.
	 *
	 *	TODO:  Add a message notifying the Resource tool that a resource has been added to a collection.
	 *
	 *	@param sessionid a valid sessionid
	 *	@param name of the resource to be added
	 *	@param collectionId of the collection it is to be added to
	 *	@param contentMime content string
	 *	@param description of the resource to be added
	 *	@param binary if true, content is encoded using Base64, if false content is assumed to be text.
	 *	@return 'Success' or 'Failure'
	 *
	 *	@author David Horwitz
	 */
    @WebMethod
    @Path("/createContentItem")
    @Produces("text/plain")
    @GET
    public String createContentItem(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "name", partName = "name") @QueryParam("name") String name,
            @WebParam(name = "collectionId", partName = "collectionId") @QueryParam("collectionId") String collectionId,
            @WebParam(name = "contentMime", partName = "contentMime") @QueryParam("contentMime") String contentMime,
            @WebParam(name = "description", partName = "description") @QueryParam("description") String description,
            @WebParam(name = "type", partName = "type") @QueryParam("type") String type,
            @WebParam(name = "binary", partName = "binary") @QueryParam("binary") boolean binary) {

        try {
			//establish the session
			Session s = establishSession(sessionid);

			ResourcePropertiesEdit props = contentHostingService.newResourceProperties();
			props.addProperty (ResourceProperties.PROP_DISPLAY_NAME, name);
			props.addProperty (ResourceProperties.PROP_DESCRIPTION, description);

			byte[] content = null;
			if (binary) {
				content = base64.decode(contentMime);
				log.info("createContentItem(): File of size: " + content + " found");	
			}
			else {
				content = contentMime.getBytes();
			}
			
			ContentResource cont = contentHostingService.addResource(name, collectionId, 10, type, content, props ,0);

			return "success";
		}
	
		catch (Exception e) {
			log.error("createContentItem(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return "failure";
	}


	/**
	 *	Update the contents of a resource.  The resource is passed either as text or encoded using Base64 flagged
	 *	using the binary parameter.
	 *
	 *	TODO:  Add a message notifying the Resource tool that a resource has been updated.
	 *
	 *	@param sessionid a valid sessionid
	 *	@param resourceId of the resource
	 *	@param contentMime content string
	 *	@param type content type string
	 *	@param binary if true, content is encoded using Base64, if false content is assumed to be text.
	 *	@return 'Success' or 'Failure'
	 *
	 *	@author David Horwitz
	 */
    @WebMethod
    @Path("/updateContentItem")
    @Produces("text/plain")
    @GET
    public String updateContentItem(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "resourceId", partName = "resourceId") @QueryParam("resourceId") String resourceId,
            @WebParam(name = "contentMime", partName = "contentMime") @QueryParam("contentMime") String contentMime,
            @WebParam(name = "type", partName = "type") @QueryParam("type") String type,
            @WebParam(name = "binary", partName = "binary") @QueryParam("binary") boolean binary) {

        try {
			//establish the session
			Session s = establishSession(sessionid);

			//  Extract content.
			byte[] content = null;
			if (binary) {
				content = base64.decode(contentMime);
			}
			else {
				content = contentMime.getBytes();
			}
			
			contentHostingService.updateResource(resourceId, type, content);
			
			return "success";
		}
	
		catch (Exception e) {
			log.error("updateContentItem(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return "failure";
	}

	/**
	 * Return the immutable content fingerprint needed before preparing a guarded resource update.
	 *
	 * @param sessionid a valid sessionid
	 * @param resourceId id of the resource
	 * @return XML with content type, length, stored SHA-256, display name, and modified date
	 */
    @WebMethod
    @Path("/getContentItemFingerprint")
    @Produces("text/plain")
    @GET
    public String getContentItemFingerprint(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "resourceId", partName = "resourceId") @QueryParam("resourceId") String resourceId) {

        try {
			Session s = establishSession(sessionid);
			ContentResource resource = contentHostingService.getResource(resourceId);

			Document dom = Xml.createDocument();
			Element item = dom.createElement("contentItemFingerprint");
			dom.appendChild(item);
			item.setAttribute("status", "success");
			addResourceFingerprintAttributes(item, resource);

			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e) {
			log.error("getContentItemFingerprint(): " + e.getClass().getName() + " : " + e.getMessage());
			return contentItemFailureResponse("contentItemFingerprint", resourceId, e.getMessage());
		}
	}

	/**
	 * Replace an existing resource only when its current content hash still matches the expected snapshot.
	 *
	 * This gives clients a non-destructive compare-and-swap write seam so automated updates do not silently
	 * overwrite a user edit that happened after the client captured the original artifact.
	 *
	 * @param sessionid a valid sessionid
	 * @param resourceId id of the resource
	 * @param expectedSha256 SHA-256 captured before approval
	 * @param contentBase64 replacement content encoded as Base64
	 * @param contentType replacement content type, or blank to preserve the current type
	 * @return XML with success, conflict, or failure status and before/after hashes
	 */
    @WebMethod
    @Path("/replaceContentItemIfSha256Matches")
    @Produces("text/plain")
    @Consumes("application/x-www-form-urlencoded")
    @POST
    public String replaceContentItemIfSha256Matches(
            @WebParam(name = "sessionid", partName = "sessionid") @FormParam("sessionid") String sessionid,
            @WebParam(name = "resourceId", partName = "resourceId") @FormParam("resourceId") String resourceId,
            @WebParam(name = "expectedSha256", partName = "expectedSha256") @FormParam("expectedSha256") String expectedSha256,
            @WebParam(name = "contentBase64", partName = "contentBase64") @FormParam("contentBase64") String contentBase64,
            @WebParam(name = "contentType", partName = "contentType") @FormParam("contentType") String contentType) {

        String normalizedExpectedSha256 = normalizeSha256(expectedSha256);

		try {
			Session s = establishSession(sessionid);

			if (StringUtils.isBlank(resourceId)) {
				return contentItemFailureResponse("contentItemUpdate", resourceId, "resourceId is required");
			}
			if (StringUtils.isBlank(normalizedExpectedSha256)) {
				return contentItemFailureResponse("contentItemUpdate", resourceId, "expectedSha256 is required");
			}
			if (StringUtils.isBlank(contentBase64)) {
				return contentItemFailureResponse("contentItemUpdate", resourceId, "contentBase64 is required");
			}

			ContentResource currentResource = contentHostingService.getResource(resourceId);
			String currentSha256 = resourceSha256(currentResource);
			byte[] replacementContent = base64.decode(contentBase64);
			String candidateSha256 = sha256Hex(replacementContent);

			if (!normalizedExpectedSha256.equals(currentSha256)) {
				Document dom = Xml.createDocument();
				Element item = dom.createElement("contentItemUpdate");
				dom.appendChild(item);

				if (candidateSha256.equals(currentSha256)) {
					item.setAttribute("status", "success");
					item.setAttribute("alreadyApplied", "true");
					item.setAttribute("message", "Replacement content is already stored on this resource.");
				}
				else {
					item.setAttribute("status", "conflict");
					item.setAttribute("message", "Current content SHA-256 does not match expectedSha256.");
				}

				setAttributeIfPresent(item, "resourceId", resourceId);
				setAttributeIfPresent(item, "expectedSha256", normalizedExpectedSha256);
				setAttributeIfPresent(item, "currentSha256", currentSha256);
				setAttributeIfPresent(item, "candidateSha256", candidateSha256);
				addResourceFingerprintAttributes(item, currentResource);
				return Xml.writeDocumentToString(dom);
			}

			String replacementContentType = contentType;
			if (StringUtils.isBlank(replacementContentType)) {
				replacementContentType = currentResource.getContentType();
			}

			ContentResource updatedResource = contentHostingService.updateResource(resourceId, replacementContentType, replacementContent);
			if (updatedResource == null) {
				updatedResource = contentHostingService.getResource(resourceId);
			}

			String updatedSha256 = resourceSha256(updatedResource);
			Document dom = Xml.createDocument();
			Element item = dom.createElement("contentItemUpdate");
			dom.appendChild(item);
			item.setAttribute("status", "success");
			setAttributeIfPresent(item, "resourceId", resourceId);
			setAttributeIfPresent(item, "previousSha256", currentSha256);
			setAttributeIfPresent(item, "currentSha256", updatedSha256);
			setAttributeIfPresent(item, "candidateSha256", candidateSha256);
			addResourceFingerprintAttributes(item, updatedResource);

			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e) {
			log.error("replaceContentItemIfSha256Matches(): " + e.getClass().getName() + " : " + e.getMessage());
			return contentItemFailureResponse("contentItemUpdate", resourceId, e.getMessage());
		}
	}

	/**
	 *	Delete the content resource given by an id (with full path).
	 *
	 *	TODO:  Add a message notifying the Resource tool that a root.has been added to a site.
	 *
	 *	@param sessionid a valid sessionid
	 *	@return the id of the resource to be deleted.
	 *
	 *	@author Mark Norton
	 */
    @WebMethod
    @Path("/deleteResource")
    @Produces("text/plain")
    @GET
    public String deleteResource(
            @WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
            @WebParam(name = "resourceId", partName = "resourceId") @QueryParam("resourceId") String resourceId) {
        try {
			//establish the session
			Session s = establishSession(sessionId);

			contentHostingService.removeResource(resourceId);
			return "success";
		}
	
		catch (Exception e) {
			log.error("deleteResource(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return "failure";
	}

	/**
	 *	Get the data associated with a resource id.  This is actually an alternative way to get a resource, since
	 *	most browsers will download a file by clicking on a link.  URLs are given by getResources() and getItem()
	 *	using the binary parameter.  It is provided for appications other than browsers that may wish to access
	 *	content hosted by a Sakai instance.
	 *
	 *	An Axis fault is thrown if the id is a collection or some other exception is thrown from the Content
	 *	Hosting Service.
	 *
	 *	@param sessionid a valid sessionid
	 *	@param collectionId of the collection it is to be added to
	 *
	 *	@return Binary data encoded as Base64.
	 *
	 *	@author Mark Norton
	 */
    @WebMethod
    @Path("/getContentData")
    @Produces("text/plain")
    @GET
    public String getContentData(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "resourceId", partName = "resourceId") @QueryParam("resourceId") String resourceId) {
        String encodedData = null;
	
		try {
			//establish the session
			Session s = establishSession(sessionid);
			
			ContentResource res = contentHostingService.getResource (resourceId);
			
			byte[] data = res.getContent();
			encodedData = base64.encodeToString(data);
		}
	
		catch (Exception e) {
			log.error("getContentData(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return encodedData;
	}

	/**
	 *	Get a resource list for the id provided.
	 *	This is returned as an XML string where each resource element has an id, name,and type child elements.
	 *	Three cases are supported at this time:
	 *	<ul>
	 *	<li>Virtual root id:  returns the list of site roots.</li>
	 *	<li>Collection id:  returns the resources in the collection.</li>
	 *	<li>Resource id:  returns a list with a single resource.</li>
	 *	</ul>
	 *
	 *	@param a valid session id.
	 *	@param id of virtual root, collection, or resource.
	 *	@return an empty string if no resources in this collection or an XML list of resource ids, names, and types.
	 *
	 *	@author Mark Norton
	 */

    @WebMethod
    @Path("/getResources")
    @Produces("text/plain")
    @GET
    public String getResources(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "id", partName = "id") @QueryParam("id") String id) {

        Session session = establishSession(sessionid);
		String ret = "";

		Document dom = Xml.createDocument();
		Node list = dom.createElement("list");
		dom.appendChild(list);

		if (id.compareTo (ContentHosting.VIRTUAL_ROOT_ID) == 0) {
			try {
				List sites = siteService.getSites(SelectionType.UPDATE, null, null, null, SortType.TITLE_ASC, null);

				for (Iterator i = sites.iterator(); i.hasNext();) {
					Site site = (Site)i.next();
					String rootId = contentHostingService.getSiteCollection(site.getId());

					try {
						// If site has no CHS root, it will throw unused id exception.
						ContentCollection coll = contentHostingService.getCollection (rootId);

						Node item = getResourceBlock (coll, dom);

						list.appendChild(item);
					}
					catch (IdUnusedException ex) {
						continue;
					}
				}

			}
			catch (Exception e) {
				return e.getClass().getName() + " : " + e.getMessage();
			}
		}
		else {
			try {
				ContentCollection coll = contentHostingService.getCollection (id);
				List entities = coll.getMemberResources();
				
				for (Iterator i = entities.iterator(); i.hasNext();) {
					ContentEntity ent = (ContentEntity)i.next();
					
					Node item = getResourceBlock (ent, dom);
					list.appendChild(item);
				}
			}
			catch (TypeException ex1) {
				try {
					ContentResource res = contentHostingService.getResource (id);
					Node item = getResourceBlock (res, dom);
					list.appendChild(item);
				}
				catch (Exception ex2) {
					return ex2.getClass().getName() + " : " + ex2.getMessage();
				}
			}
			catch (IdUnusedException ex3) {
				try {
					ContentResource res = contentHostingService.getResource (id);
					Node item = getResourceBlock (res, dom);
					list.appendChild(item);
				}
				catch (Exception ex4) {
					return ex4.getClass().getName() + " : " + ex4.getMessage();
				}
			}
			catch (Exception ex5) {
				return ex5.getClass().getName() + " : " + ex5.getMessage();
			}
		}
		
		ret = Xml.writeDocumentToString(dom);
		return ret;
	}


	/**
	 *	Get information for a resource or collection..
	 *	This is returned as an XML string in a resource element that has an id, name,and type child elements.
	 *	Three cases are supported at this time:
	 *	<ul>
	 *	<li>Virtual root id:  returns hard coded information.</li>
	 *	<li>Collection id:  returns the collection information.</li>
	 *	<li>Resource id:  returns the resource information.</li>
	 *	</ul>
	 *
	 *	@param a valid session id.
	 *	@param id of the virtual root, a collection, or a resource.
	 *	@return an empty string if no resources in this collection or an XML list of resource ids, names, and types.
	 *
	 *	@author Mark Norton
	 */
    @WebMethod
    @Path("/getInfo")
    @Produces("text/plain")
    @GET
    public String getInfo(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "id", partName = "id") @QueryParam("id") String id) {
        Session session = establishSession(sessionid);
		String ret = "";

		Document dom = Xml.createDocument();

		if (id.compareTo (ContentHosting.VIRTUAL_ROOT_ID) == 0) {
			Node item = getVirtualBlock (dom);
			dom.appendChild(item);
		}
		else {
			try {
				ContentCollection coll = contentHostingService.getCollection (id);
				Node item = getResourceBlock (coll, dom);
				dom.appendChild(item);
			}
			catch (TypeException ex1) {
				try {
					ContentResource res = contentHostingService.getResource (id);
					Node item = getResourceBlock (res, dom);
					dom.appendChild(item);
				}
				catch (Exception ex2) {
					return ex2.getClass().getName() + " : " + ex2.getMessage();
				}
			}
			catch (IdUnusedException ex3) {
				try {
					ContentResource res = contentHostingService.getResource (id);
					Node item = getResourceBlock (res, dom);
					dom.appendChild(item);
				}
				catch (Exception ex4) {
					return ex4.getClass().getName() + " : " + ex4.getMessage();
				}
			}
			catch (Exception ex5) {
				return ex5.getClass().getName() + " : " + ex5.getMessage();
			}
		}
		
		ret = Xml.writeDocumentToString(dom);
		return ret;
	}


	private String contentItemFailureResponse(String rootName, String resourceId, String message) {
		Document dom = Xml.createDocument();
		Element item = dom.createElement(rootName);
		dom.appendChild(item);
		item.setAttribute("status", "failure");
		setAttributeIfPresent(item, "resourceId", resourceId);
		setAttributeIfPresent(item, "message", message);
		return Xml.writeDocumentToString(dom);
	}

	private void addResourceFingerprintAttributes(Element item, ContentResource resource) throws Exception {
		setAttributeIfPresent(item, "resourceId", resource.getId());
		setAttributeIfPresent(item, "contentType", resource.getContentType());
		item.setAttribute("contentLength", String.valueOf(resource.getContentLength()));
		setAttributeIfPresent(item, "sha256", resourceSha256(resource));

		ResourceProperties props = resource.getProperties();
		if (props != null) {
			setAttributeIfPresent(item, "displayName", props.getProperty(ResourceProperties.PROP_DISPLAY_NAME));
			setAttributeIfPresent(item, "modifiedDate", props.getProperty(ResourceProperties.PROP_MODIFIED_DATE));
		}
	}

	private void setAttributeIfPresent(Element item, String name, String value) {
		if (StringUtils.isNotBlank(value)) {
			item.setAttribute(name, value);
		}
	}

	private String resourceSha256(ContentResource resource) throws Exception {
		String sha256 = normalizeSha256(resource.getContentSha256());
		if (StringUtils.isBlank(sha256)) {
			sha256 = sha256Hex(resource.getContent());
		}
		return sha256;
	}

	private String normalizeSha256(String sha256) {
		return StringUtils.trimToEmpty(sha256).toLowerCase(Locale.ROOT);
	}

	private String sha256Hex(byte[] content) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(content);
		char[] hex = new char[hash.length * 2];
		for (int i = 0; i < hash.length; i++) {
			int value = hash[i] & 0xff;
			hex[i * 2] = HEX_CHARS[value >>> 4];
			hex[i * 2 + 1] = HEX_CHARS[value & 0x0f];
		}
		return new String(hex);
	}


	/*
	 *  Given a content entity, returns the following information as an XML node:
	 *  <ul>
	 *  <li>id - resource identifier</li>
	 *  <li>name - display name</li>
	 *  <li>type - resource type</li>
	 *  </ul>
	 *
	 *  @author Mark Norton
	 */
	private Node getResourceBlock (ContentEntity entity, Document dom) {
		String id = null;	//  The resource or collection id.
		String name = null;	//  The display name.
		String type = null;	//  The resource type.
		String url = null;	//  The URL.
		
		// Get the content entity id.
		id = entity.getId();
	
		// Get the display name.
		ResourceProperties props = entity.getProperties();
		name = props.getProperty(ResourceProperties.PROP_DISPLAY_NAME);

		// Get the resource type.
		if (entity.isCollection())
			type = ContentHosting.RESOURCE_TYPE_COLLECTION;
		else
			type = ContentHosting.RESOURCE_TYPE_RESOURCE;

		// Get the URL for the ContentEntity.
		url = entity.getUrl();
		
	
		//  Create the resource element.
		Node item = dom.createElement("resource");

		//  Create and append the id child element.
		Node resId = dom.createElement("id");
		resId.appendChild (dom.createTextNode(id));
		item.appendChild(resId);

		//  Create and append the name child element.
		Node resName = dom.createElement("name");
		resName.appendChild( dom.createTextNode(name) );
		item.appendChild(resName);

		//  Create and append the type child element.
		Node resType = dom.createElement("type");
		resType.appendChild(dom.createTextNode(type));
		item.appendChild(resType);
		
		//  Create and append the URL child element.
		Node resUrl = dom.createElement("url");
		resUrl.appendChild(dom.createTextNode(url));
		item.appendChild(resUrl);

		return item;
	}


	/*
	 *  Create a resouce XML node for the virtual root containing:
	 *  <ul>
	 *  <li>id - resource identifier</li>
	 *  <li>name - display name</li>
	 *  <li>type - resource type</li>
	 *  </ul>
	 *
	 *  @author Mark Norton
	 */
	private Node getVirtualBlock (Document dom) {
		String id = ContentHosting.VIRTUAL_ROOT_ID;
		String name = ContentHosting.VIRTUAL_ROOT_NAME;
		String type = ContentHosting.RESOURCE_TYPE_COLLECTION;
	
		//  Create the resource element.
		Node item = dom.createElement("resource");

		//  Create and append the id child element.
		Node resId = dom.createElement("id");
		resId.appendChild (dom.createTextNode(id));
		item.appendChild(resId);

		//  Create and append the name child element.
		Node resName = dom.createElement("name");
		resName.appendChild( dom.createTextNode(name) );
		item.appendChild(resName);

		//  Create and append the type child element.
		Node resType = dom.createElement("type");
		resType.appendChild(dom.createTextNode(type));
		item.appendChild(resType);
		
		return item;
	}
	
	// Return XML document listing all resources user has in default (My Workspace) site
    @WebMethod
    @Path("/getMyWorkspaceResources")
    @Produces("text/plain")
    @GET
    public String getMyWorkspaceResources(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid) {
        return getUserResources( sessionid, null );
 	}

	// Return XML document listing all resources user has in (optional) site
    @WebMethod
    @Path("/getUserResources")
    @Produces("text/plain")
    @GET
    public String getUserResources(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
        Session session = establishSession(sessionid);
		String collectionId = null;
	
		if ( siteId == null || siteId.equals("") ) {
			collectionId = "/user/" + session.getUserId() + "/";
   		} 
		else {
		collectionId = "/group/" + siteId + "/";
		}
   
		try {
			List allResources = contentHostingService.getAllResources( collectionId );

			if ( allResources == null || allResources.size() == 0 ) {
					return "<list/>";
			}

			Document dom = Xml.createDocument();
			Node list = dom.createElement("list");
			dom.appendChild(list);

			for (Iterator i = allResources.iterator(); i.hasNext();) {
					ContentEntity entity = (ContentEntity)i.next();
					Node item = dom.createElement("item");
					String url = entity.getUrl().replaceFirst("/access/content/","/web/");
					item.appendChild( dom.createTextNode(url) );
					list.appendChild(item);
			}

			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e) {
			return "failure";
		}
	}

	/**
	 * Synchronize resources from a source site to a destination site. Accepts a sourceSiteId and a destinationSiteId. 
	 * If the resource is not present in the destination it copies it there. If the resource does exist in the 
	 * destination it performs an update if the source resource's mod date is greater than the destination's.
	 * 
	 * Note: It will not do deletes because there could be legitimate reasons for additional files in the destination
	 * site.
	 *
	 * @param 	sessionid 		the id of a valid session
	 * @param 	siteidsource	the id of the site to base this new site on
	 * @param 	siteiddest		the id of the new site (ie test123)
	 * @return					success or exception message
	 *
     *	@author Paul Dagnall
	 */

    @WebMethod
    @Path("/syncResources")
    @Produces("text/plain")
    @GET
    public String syncResources(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteidsource", partName = "siteidsource") @QueryParam("siteidsource") String siteidsource,
            @WebParam(name = "siteiddest", partName = "siteiddest") @QueryParam("siteiddest") String siteiddest) {
        Session session = establishSession(sessionid);
		
		try {
			//check only admin
			if (!securityService.isSuperUser()) {
				log.warn("syncResources(): Permission denied. Restricted to admin users.");
				return "WS syncResources(): Permission denied. Restricted to admin users.";
			}
			
			// Get all resources for source
			List<ContentResource> sourceSiteResourceList = null;
			String sourceFolderPath = "/group/" + siteidsource + "/";
			String destFolderPath = "/group/" + siteiddest + "/";
			sourceSiteResourceList = contentHostingService.getAllResources(sourceFolderPath);
			
			// Iterate through each source site resource. Copy/update if necessary
			for (ContentResource sourceResource: sourceSiteResourceList) {
				
				// Use a string replace to determine what the destination directory path (also id) should be.
				String destResourceId = sourceResource.getId().replaceAll(siteidsource, siteiddest);
				String destResourceDirectoryPath = destResourceId.substring(0,destResourceId.lastIndexOf('/')+1);
				
				// Does the resource exist? If so, check if it needs updated
				try {
					ContentResource destResource = contentHostingService.getResource(destResourceId);
					
					// Id was found so get LastModifiedDates and compare to determine if an updated occurred.
					ContentResourceEdit sourceEdit = contentHostingService.editResource(sourceResource.getId());
					ResourcePropertiesEdit sourceProps = sourceEdit.getPropertiesEdit();
					String sourceResourceModDateStr = sourceProps.getProperty(ResourceProperties.PROP_MODIFIED_DATE);
					contentHostingService.cancelResource(sourceEdit); // Needed to prevent InUseException
					Long sourceResourceModDate = Long.parseLong(sourceResourceModDateStr.trim());
					
					ContentResourceEdit destEdit = contentHostingService.editResource(destResourceId);
					ResourcePropertiesEdit destProps = destEdit.getPropertiesEdit();
					String destResourceModDateStr = destProps.getProperty(ResourceProperties.PROP_MODIFIED_DATE);
					contentHostingService.cancelResource(destEdit); // Needed to prevent InUseException
					Long destResourceModDate = Long.parseLong(destResourceModDateStr.trim());
					
					// Dates will be formatted like this 20101122153619691 for Nov 22, 2010...
					if (sourceResourceModDate > destResourceModDate) { // if source is newer, update
						contentHostingService.removeAllLocks(destResourceId); // assume priority
						contentHostingService.updateResource(destResourceId, null, sourceResource.getContent());
					}

				} catch (IdUnusedException e) { 
					// Resource doesn't exist in destination so let's copy it
					contentHostingService.copyIntoFolder(sourceResource.getId(), destResourceDirectoryPath);
				}
			}
		
		}
		catch (Exception e) {  
			log.error ("syncResources(): " + e.getClass().getName() + " : " + e.getMessage());
		 	return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

    /**
	 *  Hide or un-hide a sites resources
	 *
	 *  @param sessionId a valid sessionId
	 *  @param siteId the site to set the hidden status on
	 *  @param isHidden whether the collection is hidden or not
	 *  @return "success" or "failure"
	 */
    @WebMethod
    @Path("/siteHideResources")
    @Produces("text/plain")
    @GET
    public String siteHideResources(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionId,
            @WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteId,
            @WebParam(name = "hidden", partName = "hidden") @QueryParam("hidden") Boolean isHidden) {

		if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(siteId) || isHidden == null) {
			log.warn("WebService siteHideResources: sessionid, siteid, hidden are required");
			return "failure";
		}

		// establish the session
		establishSession(sessionId);

		Site site = null;
		try {
			 site = siteService.getSite(siteId);
			 if (siteService.isSpecialSite(site.getId())) {
				log.warn("siteHideResources: cannot run on a special site: {}", siteId);
				return "failure";
			 }
		} catch (IdUnusedException iue) {
			log.warn("WebService siteHideResources: site " + siteId + " doesn't exist");
			return "failure";
		}

		String rootCollection = contentHostingService.getSiteCollection(site.getId());
		try {
			ContentCollection collection = contentHostingService.getCollection(rootCollection);
			List<ContentEntity> entities = collection.getMemberResources();
			for (ContentEntity entity : entities) {
				if (isHidden != entity.isHidden()) {
					if (entity.isCollection()) {
						ContentCollectionEdit edit = contentHostingService.editCollection(entity.getId());
						edit.setAvailability(isHidden, null, null);
						contentHostingService.commitCollection(edit);
					} else if (entity.isResource()) {
						ContentResourceEdit edit = contentHostingService.editResource(entity.getId());
						edit.setAvailability(isHidden, null, null);
						contentHostingService.commitResource(edit);
					}
				}
			}
		} catch (Exception e) {
			log.warn("WebService siteHideResources: cannot hide items in the root collection, " + e.getMessage());
			return "failure";
		}

		return "success";
	}
}
