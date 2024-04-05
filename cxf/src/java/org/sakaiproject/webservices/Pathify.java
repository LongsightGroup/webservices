package org.sakaiproject.webservices;

import lombok.extern.slf4j.Slf4j;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.ViewableFilter;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.assignment.api.model.AssignmentSubmission;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.message.api.Message;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.assessment.data.dao.grading.AssessmentGradingData;
import org.sakaiproject.tool.assessment.facade.PublishedAssessmentFacade;
import org.sakaiproject.tool.assessment.facade.PublishedAssessmentFacadeQueries;
import org.sakaiproject.tool.assessment.services.assessment.PublishedAssessmentService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private static final int PATHIFY_DAYS_AFTER = 14;
	private static final DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	
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
			log.error("Exception in getSitesForUserForTerm: ", e);
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

				Instant openTime = a.getOpenDate();
				Instant closeTime = a.getCloseDate();
				
				if (openTime != null) {
					uElement.setAttribute("openTime", isoFormat.format(Date.from(openTime)));
				}
				if (closeTime != null) {
					uElement.setAttribute("closeTime", isoFormat.format(Date.from(closeTime)));
				}
                uElement.setAttribute("dueTime", isoFormat.format(Date.from(dueTime)));

                all.appendChild(uElement);
			}

            return Xml.writeDocumentToString(dom);
    	}
    	catch (Exception e) {
    		log.error("WS getAssignmentsForContext(): " + e.getClass().getName() + " : " + e.getMessage());
    	}
    	
    	return "<assignments/ >";
    }

	@WebMethod
	@Path("/getAssessmentsDueSoon")
	@Produces("text/xml")
	@GET
	public String getAssessmentsDueSoon(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) {

		establishPathifySession(sessionid);

		PublishedAssessmentService publishedAssessmentService = new PublishedAssessmentService();

		Document dom = Xml.createDocument();
		Node all = dom.createElement("assessments");
		dom.appendChild(all);

		String currentUserId = pathifyFlipSession(eid);
		try {
			List<PublishedAssessmentFacade> assessments = publishedAssessmentService.getBasicInfoOfAllPublishedAssessments(null, PublishedAssessmentFacadeQueries.DUE, true, siteId);
			log.debug("Got this many assessments: {}", assessments.size());

			for (PublishedAssessmentFacade a : assessments) {
				Instant dueTime = a.getDueDate().toInstant();
				if (dueTime.isBefore(Instant.now())) continue;

				Instant nowPlusDays = Instant.now().plus(Duration.ofDays(PATHIFY_DAYS_BEFORE_DUE));
				if (dueTime.isAfter(nowPlusDays)) continue;

				Element uElement = dom.createElement("assessment");
				uElement.setAttribute("id", a.getPublishedAssessmentId().toString());
				uElement.setAttribute("title", a.getTitle());
                uElement.setAttribute("dueTime", isoFormat.format(Date.from(dueTime)));
                all.appendChild(uElement);
			}
			
		}
		catch (Exception e) {
			log.error("WS getAssessmentsDueSoon(): " + e.getMessage(), e);
		}
		finally {
			if (currentUserId != null) {
				pathifyFlipSession(currentUserId);
			}
		}

        return Xml.writeDocumentToString(dom);
	}


	@WebMethod
    @Path("/getAssignmentsGradedRecently")
    @Produces("text/xml")
    @GET
    public String getAssignmentsGradedRecently(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) {
		establishPathifySession(sessionid);

		Document dom = Xml.createDocument();
		Node all = dom.createElement("assignments");
		dom.appendChild(all);

		try {
			User student = userDirectoryService.getUserByEid(eid);

			for (Assignment a : assignmentService.getAssignmentsForContext(siteId)) {
				if (a.getDraft()) continue;

				AssignmentSubmission s = assignmentService.getSubmission(a.getId(), student);
				if (!s.getGraded() || !s.getReturned()) continue;

				Instant gradedDate = s.getDateReturned();
				Instant nowMinusDays = Instant.now().minus(Duration.ofDays(PATHIFY_DAYS_AFTER));
				if (gradedDate.isBefore(nowMinusDays)) continue;

				Element uElement = dom.createElement("assignment");
				uElement.setAttribute("id", a.getId());
				uElement.setAttribute("title", a.getTitle());
				uElement.setAttribute("grade", s.getGrade());
				uElement.setAttribute("gradedDate", isoFormat.format(Date.from(gradedDate)));
			}
		}
		catch (Exception e) {
			log.error("WS getAssignmentsGradedRecently(): " + e.getClass().getName() + " : " + e.getMessage());
		}

        return Xml.writeDocumentToString(dom);
	}

	@WebMethod
    @Path("/getAssessmentsGradedRecently")
    @Produces("text/xml")
    @GET
    public String getAssessmentsGradedRecently(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) {
		establishPathifySession(sessionid);

		PublishedAssessmentService publishedAssessmentService = new PublishedAssessmentService();

		Document dom = Xml.createDocument();
		Node all = dom.createElement("assessments");
		dom.appendChild(all);

		String currentUserId = pathifyFlipSession(eid);
		try {
			List<AssessmentGradingData> assessments = publishedAssessmentService.getBasicInfoOfLastOrHighestOrAverageSubmittedAssessmentsByScoringOption(eid, siteId, false);
			log.debug("Got this many AssessmentGradingData: {}", assessments.size());

			for (AssessmentGradingData a : assessments) {
				Instant gradedDate = a.getGradedDate().toInstant();
				Instant submittedDate = a.getSubmittedDate().toInstant();
				Instant nowMinusDays = Instant.now().minus(Duration.ofDays(PATHIFY_DAYS_AFTER));
				if (gradedDate == null || gradedDate.isBefore(nowMinusDays)) continue;

				Element uElement = dom.createElement("assessment");
				uElement.setAttribute("id", a.getPublishedAssessmentId().toString());
				uElement.setAttribute("title", a.getPublishedAssessmentTitle());
				uElement.setAttribute("grade", a.getFinalScore().toString());
                uElement.setAttribute("gradedDate", isoFormat.format(Date.from(gradedDate)));
                if (submittedDate != null) {
					uElement.setAttribute("submittedDate", isoFormat.format(Date.from(submittedDate)));
				}
				all.appendChild(uElement);
			}

		}
		catch (Exception e) {
			log.error("WS getAssessmentsGradedRecently(): " + e.getMessage(), e);
		}
		finally {
			if (currentUserId != null) {
				pathifyFlipSession(currentUserId);
			}
		}

        return Xml.writeDocumentToString(dom);
	}


	@WebMethod
    @Path("/getRecentAnnouncements")
    @Produces("text/xml")
    @GET
    public String getRecentAnnouncements(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) {

		establishPathifySession(sessionid);

		Document dom = Xml.createDocument();
		Node all = dom.createElement("announcements");
		dom.appendChild(all);

		String channelRef = announcementService.channelReference(siteId, siteService.MAIN_CONTAINER);
		ViewableFilter vf = new ViewableFilter(null, null, 99, announcementService);
        try {
            List<Message> messages = announcementService.getMessages(channelRef, vf, true, false);
			for (Message o : messages) {
				// TODO: date filtering code
				AnnouncementMessage msg = (AnnouncementMessage) o;
				Element uElement = dom.createElement("announcement");
				uElement.setAttribute("id", msg.getId());
				uElement.setAttribute("title", msg.getAnnouncementHeader().getSubject());
				uElement.setAttribute("url", msg.getUrl());
				uElement.setAttribute("pubDate", isoFormat.format(msg.getHeader().getInstant()));
				// Create a new text node for the body
				Node bodyNode = dom.createTextNode(msg.getBody());
				uElement.appendChild(bodyNode);
				all.appendChild(uElement);
			}
        } catch (IdUnusedException | PermissionException e) {
			log.warn("Could not get messages for site {}", siteId, e);
		}

        return Xml.writeDocumentToString(dom);
	}


	@WebMethod(exclude = true)
	private void establishPathifySession(String sessionid) {
        Session s = sessionManager.getSession(sessionid);

        if (s == null) {
            throw new RuntimeException("Session \"" + sessionid + "\" is not active");
        }
        s.setActive();
        sessionManager.setCurrentSession(s);
        if (!s.getUserEid().equals("pathify")) {
            throw new RuntimeException("Incorrect user");
        }
		//s.setUserId("admin");
    }

	@WebMethod(exclude = true)
	private String pathifyFlipSession(String eid) throws RuntimeException {
		Session currentSession = sessionManager.getCurrentSession();
        User flipTo = null;
        try {
            flipTo = userDirectoryService.getUserByEid(eid);
        } catch (UserNotDefinedException e) {
            throw new RuntimeException(e);
        }
        String oldUserId = currentSession.getUserId();
		currentSession.setUserId(flipTo.getId());
		sessionManager.setCurrentSession(currentSession);
		return oldUserId;
	}

}
