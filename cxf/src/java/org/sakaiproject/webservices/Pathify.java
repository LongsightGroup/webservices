package org.sakaiproject.webservices;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.gradebook.Gradebook;
import org.sakaiproject.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import lombok.extern.slf4j.Slf4j;

/**
 * Pathify
 * <p/>
 * A set of custom web services for Pathify Sakai
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
@Slf4j
public class Pathify extends AbstractWebService {

	private static final int PATHIFY_DAYS_BEFORE_DUE = 30;
	
	@WebMethod
	@Path("/getSitesForUserForTerm")
	@Produces("text/xml")
	@GET
	public String getSitesForUserForTerm(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid,
			@WebParam(name = "term", partName = "term") @QueryParam("term") String term)
	{

		establishPathifySession(sessionId);

		Document dom = Xml.createDocument();
		Node siteList = dom.createElement("sites");
		dom.appendChild(siteList);

		try {
			SelectionType selectionType = SelectionType.ACCESS;
			// selectionType = SelectionType.MEMBER;
			Map<String,String> termProp = new HashMap<>();
			termProp.put(Site.PROP_SITE_TERM, term);
			List<Site> sites = siteService.getSites(selectionType, null, null, termProp, SortType.TITLE_ASC, null);

			for (Site s : sites) {
				String siteId = s.getId();
				String siteTitle = s.getTitle();

				// Create a new site element
				Element siteElement = dom.createElement("site");

				// Set id as an attribute of site element
				siteElement.setAttribute("id", siteId);

				// Provide a deep link
				siteElement.setAttribute("link", serverConfigurationService.getPortalUrl() + "/site/" + siteId);

				// Set title as an attribute of site element
				siteElement.setAttribute("title", siteTitle);

				// Append the site element to the parent element (assuming 'course' is your parent element)
				siteList.appendChild(siteElement);
			}

			return Xml.writeDocumentToString(dom);
		} catch (Exception e) {
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
	}

	@WebMethod
    @Path("/getAssignmentsDueSoon")
    @Produces("text/xml")
    @GET
    public String getAssignmentsDueSoon(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) {

		establishPathifySession(sessionid);

        try {
    		Document dom = Xml.createDocument();
    		Node all = dom.createElement("assignments");
    		dom.appendChild(all);
    		
    		for (Assignment a : assignmentService.getAssignmentsForContext(siteId)) {
				// Lots of checking in canSubmit around dates, groups, etc
    			if (a.getDraft()) continue;
				if (!assignmentService.canSubmit(a)) continue;

				Instant dueTime = a.getDueDate();
				Instant nowPlusDays = Instant.now().plus(Duration.ofDays(PATHIFY_DAYS_BEFORE_DUE));
				if (dueTime.isAfter(nowPlusDays)) continue;

				Element uElement = dom.createElement("assignment");
				uElement.setAttribute("id", a.getId());
				uElement.setAttribute("title", a.getTitle());
				
				String deepLink = assignmentService.getDeepLink(siteId, a.getId(), eid);
				uElement.setAttribute("link", deepLink);

				// Format ISO-8601
				DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				Instant openTime = a.getOpenDate();
				Instant closeTime = a.getCloseDate();
				
				if (openTime != null) {
					uElement.setAttribute("openTime", format.format(Date.from(openTime)));
				}
				if (closeTime != null) {
					uElement.setAttribute("closeTime", format.format(Date.from(closeTime)));
				}
				if (dueTime != null) {
					uElement.setAttribute("dueTime", format.format(Date.from(dueTime)));
				}
				
				all.appendChild(uElement);
			}

    		String retVal = Xml.writeDocumentToString(dom);
    		return retVal;
    	}
    	catch (Exception e) {
    		log.error("WS getAssignmentsForContext(): " + e.getClass().getName() + " : " + e.getMessage());
    	}
    	
    	return "<assignments/ >";
    }


	@WebMethod(exclude = true)
	private void establishPathifySession(String sessionid) {
        Session s = sessionManager.getSession(sessionid);

        if (s == null) {
            throw new RuntimeException("Session \"" + sessionid + "\" is not active");
        }
        s.setActive();
        sessionManager.setCurrentSession(s);
        if (!s.getUserEid().contains("pathify")) {
            throw new RuntimeException("Incorrect user");
        }
    }
}
