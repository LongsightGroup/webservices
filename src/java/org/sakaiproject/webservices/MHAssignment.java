package org.sakaiproject.webservices;

import org.sakaiproject.grading.api.Assignment;
import org.sakaiproject.grading.api.GradeDefinition;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.user.api.User;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.Date;
import java.util.Iterator;

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
public class MHAssignment extends AbstractWebService {

    public static final short USI_CREATED = 1;
    public static final short USI_UPDATED = 2;
    public static final short USI_DELETED = 3;
    public static final short USI_EXTERNAL = 4;
    public static final short USI_INTERNAL = 5;
    public static final short USI_CONFLICT = 6;
    public static final short USI_INVALID_NAME = 7;

    public static final short SC_NOTFOUND = 1;
    public static final short SC_UPDATED = 2;

    @WebMethod
    @Path("/test")
    @Produces("text/plain")
    @GET
    /**
     *
     * @return String
     */
    public String test(
        @WebParam(partName = "sessionID")
        @QueryParam("sessionID") String sessionID
    ) throws org.sakaiproject.user.api.UserNotDefinedException
	{
		// Make sure the session is valid.
		establishSession(sessionID);

		String eid;
		try
		{
			eid = userDirectoryService.getUserId(sessionID);
		}
		catch(org.sakaiproject.user.api.UserNotDefinedException e)
		{
			eid = "0fbc1625-95b1-4cd0-b12a-c19e3eeb8252";
		}

		return eid;
	}

	@WebMethod
	@Path("/UpdateScore")
	@Produces("text/plain")
	@GET
    /**
     * Updates user score in the specified assignment by external id.
     *
     * @param String sessionID
     * @param String gradebookID
     * @param String assignmentID
     * @param String userID
     * @param String Points
     * @return void
     * @throws org.sakaiproject.user.api.UserNotDefinedException
     * @throws GradebookNotFoundException
     * @throws AssessmentNotFoundException
     */
	public void UpdateScore(
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID,
		@WebParam(partName = "assignmentID") @QueryParam("assignmentID") String assignmentID,
		@WebParam(partName = "userID") @QueryParam("userID") String userID,
		@WebParam(partName = "points") @QueryParam("points") String Points
    ) throws org.sakaiproject.user.api.UserNotDefinedException
	{
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll"))
        {
 			throw new RuntimeException("Non-permitted user trying to update scores: " + s.getUserId());
        }

        // Get the user id from the directory.
        String uid = getUserId(userID);

        // Update score.
        gradingService.updateExternalAssessmentScore(gradebookID, assignmentID, uid, Points);
	}

	@WebMethod
	@Path("/UpdateScorableItem")
	@Produces("text/plain")
	@GET
    /**
     * Adds/updates MH Campus external assignments.
     *
     * @param String sessionID
     * @param String gradebookID
     * @param String assignmentID
     * @param String addUpdateRemoveAssignment
     * @param String assignment_Title
     * @param Double assignment_maxPoints
     * @param long assignment_dueTime
     * @return void
     */
	public void UpdateScorableItem (
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID,
		@WebParam(partName = "assignmentID") @QueryParam("assignmentID") String assignmentID,
		@WebParam(partName = "addUpdateRemoveAssignment") @QueryParam("addUpdateRemoveAssignment") String addUpdateRemoveAssignment,
		@WebParam(partName = "assignmentTitle") @QueryParam("assignmentTitle") String assignment_Title,
		@WebParam(partName = "assignmentMaxPoints") @QueryParam("assignmentMaxPoints") Double assignment_maxPoints,
		@WebParam(partName = "assignmentDueTime") @QueryParam("assignmentDueTime") long assignment_dueTime
    )
	{
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll"))
        {
 			throw new RuntimeException("Non-permitted user trying to update scorable item: " + s.getUserId());
        }

        // Add assignment.
        if (addUpdateRemoveAssignment.equals("add"))
        {
            if (gradingService.isExternalAssignmentDefined(gradebookID, assignmentID))
            {
                // Update assignment in gradebook.
                gradingService.updateExternalAssessment(
                    gradebookID,
                    assignmentID,
                    "",
                    null,
                    assignment_Title,
                    assignment_maxPoints,
                    new Date(assignment_dueTime),
                    false
                );
            }
            else
            {
                // Add assignment to gradebook.
                gradingService.addExternalAssessment(
                    gradebookID,
                    assignmentID,
                    "",
                    assignment_Title,
                    assignment_maxPoints,
                    new Date(assignment_dueTime),
                    "MH Gradebook",
                    null,
                    false
                );

                //release assessment to assignment
                //g.setExternalAssessmentToGradebookAssignment(gradebookID, assignmentID);
            }

        }

        // Remove.
        if (addUpdateRemoveAssignment.equals("remove"))
        {
            // Remove assignment from gradebook.
            if (gradingService.isExternalAssignmentDefined(gradebookID, assignmentID))
            {
				gradingService.removeExternalAssignment(gradebookID, assignmentID);
			}
        }
	}

	@WebMethod
	@Path("/GetAssignments")
	@Produces("text/plain")
	@GET
    /**
     * Returns JSON formatted array of assignment info objects.
     * [{"id":"assignment id","name":"assignment name","eid":"assignment external id"},...]
     *
     * @param String sessionID
     * @param String gradebookID
     * @return String
     */
    public String GetAssignments(
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID
    )
    {
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll")) {
 			throw new RuntimeException("Non-permitted user trying to get assignments: " + s.getUserId());
        }

        // Collate the assignments for the specified site (gradebook id).
        String jsonList = "";
        if (1==1) {
            Iterator<?> i = gradingService.getAssignments(gradebookID).iterator();
            for (;i.hasNext();) {
                if (!"".equals(jsonList)) {
                    jsonList = jsonList + ",";
                }

                Assignment assign = (Assignment) i.next();
                String jsonAssign = "{";
                jsonAssign += "\"id\":\"" + assign.getId().toString() + "\",";
                jsonAssign += "\"name\":\"" + assign.getName() + "\",";
                jsonAssign += "\"eid\":\"" + assign.getExternalId() + "\"";
                jsonAssign += "}";

                jsonList = jsonList + jsonAssign;
            }
        } else {
 			throw new RuntimeException("Gradebook '" + gradebookID + "' is not defined.");
        }
        String jsonReturn = "[" + jsonList + "]";
        return jsonReturn;
    }

	@WebMethod
	@Path("/GetScorableItem")
	@Produces("text/plain")
	@GET
    /**
     * Returns info of MH Campus external assignments if exists.
     *
     * @param String sessionID
     * @param String gradebookID
     * @param String assignmentID
     * @return String
     */
    public String GetScorableItem(
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID,
        @WebParam(partName = "assignmentName") @QueryParam("assignmentName") String assignmentName
    ) throws org.sakaiproject.user.api.UserNotDefinedException
    {
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll")) {
 			throw new RuntimeException("Non-permitted user trying to retrieve scores: " + s.getUserId());
        }

        // Collate the grade definitions  for the specified site (gradebook id).
        String jsonAssignment = "";
        if (1==1) {
            // Get the assignment.
            org.sakaiproject.grading.api.Assignment assignment = gradingService.getAssignment(gradebookID, assignmentName);

            if (assignment == null) {
                return "Assignment " + assignmentName + " not found.";
            }

            jsonAssignment += "\"categoryName\":\"" + assignment.getCategoryName() + "\",";
            jsonAssignment += "\"dueDate\":\"" + assignment.getDueDate().toString() + "\",";
            jsonAssignment += "\"externalAppName\":\"" + assignment.getExternalAppName() + "\",";
            jsonAssignment += "\"externalId\":\"" + assignment.getExternalId() + "\",";
            jsonAssignment += "\"id\":\"" + assignment.getId().toString() + "\",";
            jsonAssignment += "\"name\":\"" + assignment.getName() + "\",";
            jsonAssignment += "\"points\":\"" + assignment.getPoints().toString() + "\",";
            jsonAssignment += "\"ungraded\":\"" + assignment.getUngraded() + "\",";
            jsonAssignment += "\"isCounted\":\"" + assignment.getCounted() + "\",";
            jsonAssignment += "\"isExternallyMaintained\":\"" + assignment.getExternallyMaintained() + "\",";
            jsonAssignment += "\"isReleased\":\"" + assignment.getReleased() + "\"";
        }
        return "[" + jsonAssignment + "]";
    }

	@WebMethod
	@Path("/UpdateScorableItemByName")
	@Produces("text/plain")
	@GET
    /**
     * Adds new MH Campus external assignments.
     * Does not update existing assignments (either by name or external id).
     * Returns the external id of the found or created assignment.
     *
     * @param String sessionID
     * @param String gradebookID
     * @param String assignmentID
     * @param String addUpdateRemoveAssignment
     * @param String assignment_Title
     * @param Double assignment_maxPoints
     * @param long assignment_dueTime
     * @return String JSON formatted object: {"ret":"return code","eid":"assignment external ID"}.
     */
    public String UpdateScorableItemByName(
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID,
		@WebParam(partName = "assignmentID") @QueryParam("assignmentID") String assignmentID,
		@WebParam(partName = "addUpdateRemoveAssignment") @QueryParam("addUpdateRemoveAssignment") String addUpdateRemoveAssignment,
		@WebParam(partName = "assignmentTitle") @QueryParam("assignmentTitle") String assignment_Title,
		@WebParam(partName = "assignmentMaxPoints") @QueryParam("assignmentMaxPoints") Double assignment_maxPoints,
		@WebParam(partName = "assignmentDueTime") @QueryParam("assignmentDueTime") long assignment_dueTime
    )
    {
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll")) {
 			throw new RuntimeException("Non-permitted user trying to update scorable item: " + s.getUserId());
        }

        // Must have assignment name.
        if (assignment_Title.isEmpty()) {
            return usiResult(USI_INVALID_NAME, assignmentID);
        }

        // Remove.
        if ("remove".equals(addUpdateRemoveAssignment)) {
            // Remove assignment from gradebook.
            gradingService.removeExternalAssignment(gradebookID, assignmentID);
            return usiResult(USI_DELETED, assignmentID);
        }

        Boolean isAssignment = gradingService.isAssignmentDefined(gradebookID, assignment_Title);
        Boolean isExternalAssignment = gradingService.isExternalAssignmentDefined(gradebookID, assignmentID);

        // Existing assignment by name.
        if (isAssignment) {
            // Get the assignment.
            org.sakaiproject.grading.api.Assignment assignment = gradingService.getAssignment(gradebookID, assignment_Title);

            // Internally maintained, nothing to do.
            if (assignment.getExternalId() == null) {
                return usiResult(USI_INTERNAL, "");
            }

            String externalID = assignment.getExternalId();

            // An assignment with the same name but different external id already exists.
            // Name conflict.
            if (isExternalAssignment && !assignmentID.equals(externalID)) {
                return usiResult(USI_CONFLICT, externalID);
            }

            return usiResult(USI_EXTERNAL, externalID);
        }

        // An assignment with the same external id but a different name already exists.
        // We do not update existing, so this is a name conflict.
        if (isExternalAssignment) {
            return usiResult(USI_CONFLICT, assignmentID);
        }

        // New assignment.
        gradingService.addExternalAssessment(
            gradebookID,
            assignmentID,
            "",
            assignment_Title,
            assignment_maxPoints,
            new Date(assignment_dueTime),
            "MH Gradebook",
            null,
            false
        );
        return usiResult(USI_CREATED, assignmentID);
    }

	@WebMethod
	@Path("/GetScore")
	@Produces("text/plain")
	@GET
    /**
     * Returns user score for the specified user in the specified assignment by external id.
     *
     * @param String sessionID
     * @param String gradebookID
     * @param String assignmentID
     * @param String userID
     * @return String
     * @throws org.sakaiproject.user.api.UserNotDefinedException
     * @throws GradebookNotFoundException
     * @throws AssessmentNotFoundException
     */
    public String GetScore(
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID,
        @WebParam(partName = "assignmentName") @QueryParam("assignmentName") String assignmentName,
		@WebParam(partName = "userID") @QueryParam("userID") String userID
    ) throws org.sakaiproject.user.api.UserNotDefinedException
    {
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll")) {
            throw new RuntimeException("Non-permitted user trying to retrieve scores: " + s.getUserId());
        }

        // Get the user uid from the directory.
        String uid = getUserId(userID);

        // Collate the grade definitions  for the specified site (gradebook id).
        String jsonGrade = "";
        if (1==1) {
            // Get the assignment id in the gradebook.
            org.sakaiproject.grading.api.Assignment assignment = gradingService.getAssignment(gradebookID, assignmentName);

            if (assignment == null) {
                return "Assignment " + assignmentName + " not found.";
            }

            // Get the grade.
            Long gbObjectId = assignment.getId();
            GradeDefinition grade = gradingService.getGradeDefinitionForStudentForItem(gradebookID, gbObjectId, uid);

            if (grade == null) {
                return "Grade definition for " + userID + " not found.";
            }

            // Grader eid.
            String graderEid = "null";
            if (grade.getGraderUid() != null) {
                graderEid = getUserEid(grade.getGraderUid());
            }

            // Date recorded.
            String dateRecorded = "null";
            if (grade.getDateRecorded() != null) {
                dateRecorded = grade.getDateRecorded().toString();
            }

            jsonGrade += "\"studentId\":\"" + userID + "\",";
            jsonGrade += "\"graderId\":\"" + graderEid + "\",";
            jsonGrade += "\"grade\":\"" + grade.getGrade() + "\",";
            jsonGrade += "\"gradeComment\":\"" + grade.getGradeComment() + "\",";
            jsonGrade += "\"dateRecorded\":\"" + dateRecorded + "\"";
        }
        return "[" + jsonGrade + "]";
    }

	@WebMethod
	@Path("/UpdateScoreInternal")
	@Produces("text/plain")
	@GET
    /**
     * Updates the score of an internal assignment identified by the assignment name for the specified user.
     *
     * @param String sessionID
     * @param String gradebookID
     * @param String assignmentName
     * @param String userID
     * @param String Score
     * @return short
     */
    public short UpdateScoreInternal(
		@WebParam(partName = "sessionID") @QueryParam("sessionID") String sessionID,
		@WebParam(partName = "gradebookID") @QueryParam("gradebookID") String gradebookID,
        @WebParam(partName = "assignmentName") @QueryParam("assignmentName") String assignmentName,
		@WebParam(partName = "userID") @QueryParam("userID") String userID,
		@WebParam(partName = "Score") @QueryParam("userID") String Score
    )
    {
        // Check to make sure session is valid.
        Session s = establishSession(sessionID);

        // Require permission.
        if (!hasPermission(gradebookID, "gradebook.gradeAll")) {
 			throw new RuntimeException("Non-permitted user trying to update scores: " + s.getUserId());
        }

        // Get the user id from the directory.
        String uid = getUserId(userID);

        if (!gradingService.isAssignmentDefined(gradebookID, assignmentName)) {
            return SC_NOTFOUND;
        }

        // Make sure this is an internal assignment..
        org.sakaiproject.grading.api.Assignment assignment = gradingService.getAssignment(gradebookID, assignmentName);
        if (assignment.getExternallyMaintained()) {
            return SC_NOTFOUND;
        }

        // Set the assignment score for the user.
        gradingService.setAssignmentScoreString(gradebookID, assignmentName, uid, Score, "");
        return SC_UPDATED;
    }

    /**
    *
    * @param String gradebookID
    * @param String permission
    * @return boolean
    */
   private boolean hasPermission(String gradebookID, String permission) {
       // Make sure the user has update all scores permission (typically an Instructor).
       User user = userDirectoryService.getCurrentUser();
       String siteRef = siteService.siteReference(gradebookID);

       return securityService.unlock(user, permission, siteRef);
   }

    /**
     * Returns the user id from the direcotry service.
     *
     * @param String userID
     * @return String
     */
    private String getUserId(String userID) {
		String uid;
		try {
			uid = userDirectoryService.getUserId(userID);
		}
		catch(Exception e) {
			uid = userID;
		}
        return uid;
    }

    /**
     * Returns the user eid from the direcotry service.
     *
     * @param String uid
     * @return String
     */
    private String getUserEid(String uid) {
        String eid;
		try {
			eid = userDirectoryService.getUserEid(uid);
		}
		catch(Exception e) {
			eid = uid;
		}
        return eid;
    }

    /**
     * Returns retCode and external id as a json formatted object.
     *
     * @param short retCode
     * @param String externalID
     * @return String
     */
    private String usiResult(short retCode, String externalID) {
        return "{\"ret\":\"" + retCode + "\",\"eid\":\"" + externalID + "\"}";
    }

}
