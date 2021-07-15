/**
 * Copyright (c) 2005-2016 The Apereo Foundation
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

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
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

import lombok.extern.slf4j.Slf4j;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.GradeDefinition;
import org.sakaiproject.service.gradebook.shared.GradebookFrameworkService;
import org.sakaiproject.service.gradebook.shared.GradingScaleDefinition;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.gradebook.GradingScale;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.Xml;
import org.sakaiproject.tool.gradebook.GradeMapping;
import org.sakaiproject.tool.gradebook.Gradebook;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;

/**
 * Created by: Diego del Blanco, SCRIBA
 * Date: 9/18/15
 */
@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
@Slf4j
public class SakaiGradebook extends AbstractWebService {

    protected GradebookFrameworkService gradebookFrameworkService;

    @WebMethod(exclude = true)
    public void setGradebookFrameworkService(GradebookFrameworkService gradebookFrameworkService) {
        this.gradebookFrameworkService = gradebookFrameworkService;
    }


    @WebMethod
    @Path("/createOrUpdateGradeScale")
    @Produces("text/plain")
    @GET
    public String createOrUpdateGradeScale(
            @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
            @WebParam(name = "scaleUuid", partName = "scaleUuid") @QueryParam("scaleUuid") String scaleUuid,
            @WebParam(name = "scaleName", partName = "scaleName") @QueryParam("scaleName") String scaleName,
            @WebParam(name = "grades", partName = "grades") @QueryParam("grades") String[] grades,
            @WebParam(name = "percents", partName = "percents") @QueryParam("percents") String[] percents,
            @WebParam(name = "updateOld", partName = "updateOld") @QueryParam("updateOld") boolean updateOld,
            @WebParam(name = "updateOnlyNotCustomized", partName = "updateOnlyNotCustomized") @QueryParam("updateOnlyNotCustomized") boolean updateOnlyNotCustomized) {

        Session session = establishSession(sessionid);
        Map defaultBottomPercentsOld = new HashMap(); //stores the old default values, to check if a gradeSet is customized or not.

        if (!securityService.isSuperUser()) {
            log.warn("NonSuperUser trying to change Gradebook Scales: " + session.getUserId());
            throw new RuntimeException("NonSuperUser trying to change Gradebook Scales: " + session.getUserId());
        }

        try {
            boolean isUpdate = false;

            //In the case it is called as a restful service we need to read correctly the parameters
            if (percents[0].endsWith("}") && percents[0].startsWith("{") && grades[0].endsWith("}") && grades[0].startsWith("{")) {
                grades = grades[0].substring(1, grades[0].length() - 1).split(",");
                percents = percents[0].substring(1, percents[0].length() - 1).split(",");
            }

            //Get all the scales
            List<GradingScale> gradingScales = gradebookFrameworkService.getAvailableGradingScales();

            List<GradingScaleDefinition> gradingScaleDefinitions= new ArrayList<>();
            //The API returns GradingScales, but needs GradingScalingDefinitions, so we'll need to convert them.

            //Compare the UID of the scale to check if we need to update ot create a new one
            for (Iterator iter = gradingScales.iterator(); iter.hasNext();) {
                GradingScale gradingScale = (GradingScale)iter.next();
                GradingScaleDefinition gradingScaleDefintion = gradingScale.toGradingScaleDefinition();
                if (gradingScaleDefintion.getUid().equals(scaleUuid)) {   //If it is an update...

                    //Store the previous default values to compare later if updateOnlyNotCustomized=true
                    Iterator gradesIterOld = gradingScaleDefintion.getGrades().iterator();
                    Iterator defaultBottomPercentsIterOld = gradingScaleDefintion.getDefaultBottomPercentsAsList().iterator();
                    while (gradesIterOld.hasNext() && defaultBottomPercentsIterOld.hasNext()) {
                        String gradeOld = (String)gradesIterOld.next();
                        Double valueOld = (Double)defaultBottomPercentsIterOld.next();
                        defaultBottomPercentsOld.put(gradeOld, valueOld);
                    }
                    // Set the new Values
                    gradingScaleDefintion.setName(scaleName);
                    gradingScaleDefintion.setGrades(Arrays.asList(grades));
                    gradingScaleDefintion.setDefaultBottomPercentsAsList(Arrays.asList(percents));
                    isUpdate=true;
                }
                gradingScaleDefinitions.add(gradingScaleDefintion); //always add the Scale
            }

            if (!isUpdate) {   //If it is not an update we create the scale and add it.
                GradingScaleDefinition scale = new GradingScaleDefinition();
                scale.setUid(scaleUuid);
                scale.setName(scaleName);
                scale.setGrades(Arrays.asList(grades));
                scale.setDefaultBottomPercentsAsList(Arrays.asList(percents));
                gradingScaleDefinitions.add(scale);//always add the Scale
            }

            //Finally we update all the scales
            gradebookFrameworkService.setAvailableGradingScales(gradingScaleDefinitions);

            // Now we need to add this scale to ALL the actual gradebooks if it is new,
            // and if not new, then update (if updateOld=true) the values in the ALL the old gradebooks.
            // Seems that there is not any service that returns the full list of all the gradebooks,
            // but with the siteid we can call gradebookService.isGradebookDefined(siteId)
            // and know if the site has gradebook or not, and use gradebookService.getGradebook(siteId); to
            // have all of them.

            List<String> siteList = siteService.getSiteIds(SelectionType.NON_USER, null, null, null, SortType.NONE, null);

            for (String siteId : siteList) {
                if (gradebookService.isGradebookDefined(siteId)){
                    //If the site has gradebook then we
                    Gradebook gradebook = (Gradebook)gradebookService.getGradebook(siteId);
                    String gradebookUid=gradebook.getUid();
                    Long gradebookId=gradebook.getId();

                    if (!isUpdate) { //If it is new then we need to add the scale to every actual gradebook in the list
                            gradebookFrameworkService.saveGradeMappingToGradebook(scaleUuid, gradebookUid);
                            log.debug("SakaiGradebook: Adding the new scale " + scaleUuid + " in gradebook: " + gradebook.getUid());

                    }else{ //If it is not new, then update the actual gradebooks with the new values ONLY if updateOld is true
                        if (updateOld)  {
                            Set<GradeMapping> gradeMappings =gradebookService.getGradebookGradeMappings(gradebookId);
                                for (Iterator iter2 = gradeMappings.iterator(); iter2.hasNext();) {
                                    GradeMapping gradeMapping = (GradeMapping)iter2.next();
                                    if (gradeMapping.getGradingScale().getUid().equals(scaleUuid)){
                                        if (updateOnlyNotCustomized){ //We will only update the ones that teachers have not customized
                                            if (mapsAreEqual(defaultBottomPercentsOld, gradeMapping.getGradeMap())){
                                                log.debug("SakaiGradebook:They are equals " + gradebook.getUid());
                                                gradeMapping.setDefaultValues();
                                            }else{
                                                log.debug("SakaiGradebook:They are NOT equals " + gradebook.getUid());
                                            }
                                        }else{
                                            gradeMapping.setDefaultValues();
                                        }
                                        log.debug("SakaiGradebook: updating gradeMapping" + gradeMapping.getName());
                                        gradebookFrameworkService.updateGradeMapping(gradeMapping.getId(),gradeMapping.getGradeMap());
                                    }
                                }

                        }
                    }

                }
            }
        } catch (Exception e) {
            log.error("SakaiGradebook: createOrUpdateGradeScale: Error attempting to manage a gradescale " + e.getClass().getName() + " : " + e.getMessage());
            return e.getClass().getName() + " : " + e.getMessage();
        }
        return "success";
    }

    @WebMethod
    @Path("/addExternalAssessment")
    @Produces("text/plain")
    @GET
	public String addExternalAssessment(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "externalId", partName = "externalId") @QueryParam("externalId") String externalId,
			@WebParam(name = "externalUrl", partName = "externalUrl") @QueryParam("externalUrl") String externalUrl,
			@WebParam(name = "assignmentName", partName = "assignmentName") @QueryParam("assignmentName") String assignmentName,
			@WebParam(name = "longVarValue", partName = "longVarValue") @QueryParam("longVarValue") String longVarValue,
			@WebParam(name = "toolName", partName = "toolName") @QueryParam("toolName") String toolName) {

		Session s = establishSession(sessionid);

		try {
			Calendar calVar = Calendar.getInstance();
			Date currentTimeVar = calVar.getTime();
			long longVar = Long.parseLong(longVarValue);

			gradebookExternalAssessmentService.addExternalAssessment(gradebookUid, externalId, externalUrl, assignmentName, longVar, currentTimeVar, toolName, null);
		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

    @WebMethod
    @Path("/updateExternalAssessment")
    @Produces("text/plain")
    @GET
	public String updateExternalAssessment(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "externalId", partName = "externalId") @QueryParam("externalId") String externalId,
			@WebParam(name = "externalUrl", partName = "externalUrl") @QueryParam("externalUrl") String externalUrl,
			@WebParam(name = "assignmentName", partName = "assignmentName") @QueryParam("assignmentName") String assignmentName,
			@WebParam(name = "longVarValue", partName = "longVarValue") @QueryParam("longVarValue") String longVarValue) {

		Session s = establishSession(sessionid);

		try {
			Calendar calVar = Calendar.getInstance();
			Date currentTimeVar = calVar.getTime();
			long longVar = Long.parseLong(longVarValue);

			gradebookExternalAssessmentService.updateExternalAssessment(gradebookUid, externalId, externalUrl, null, assignmentName, longVar, currentTimeVar);

		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

    @WebMethod
    @Path("/isGradeBookDefined")
    @Produces("text/plain")
    @GET
	public String isGradeBookDefined(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid) {

		Session s = establishSession(sessionid);

		try {
			if (gradebookExternalAssessmentService.isGradebookDefined(gradebookUid)) {
				return "true";
			} else {
				return "false";
			}

		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
	}

    @WebMethod
    @Path("/updateExternalAssessmentWithScoreList")
    @Produces("text/plain")
    @GET
	public String updateExternalAssessmentWithScoreList(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "externalId", partName = "externalId") @QueryParam("externalId") String externalId,
			@WebParam(name = "studentList", partName = "studentList") @QueryParam("studentList") String studentList,
			@WebParam(name = "scoreList", partName = "scoreList") @QueryParam("scoreList") String scoreList) {

		Session s = establishSession(sessionid);

		String errlist = "";

		try {
			long externalIdLong = new Long(externalId).longValue();

			String[] inputtedLoginNameArray = studentList.split(",");
			String[] inputtedGradeArray = scoreList.split(",");
			for (int x = 0; x < inputtedLoginNameArray.length; x++) {
				String studentID = inputtedLoginNameArray[x];
				String pointsStringVar = inputtedGradeArray[x];
				Double points = new Double(pointsStringVar);
				log.warn("trying to submit grade for" + studentID);

				if (gradebookService.isUserAbleToGradeItemForStudent(gradebookUid, externalIdLong, studentID)) {
					log.warn("submit grade for" + studentID + ":" + points);
					gradebookExternalAssessmentService.updateExternalAssessmentScore(gradebookUid, externalId,
							studentID, pointsStringVar);
				} else
					errlist = errlist + "," + studentID;
			}

		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

		if (errlist.length() == 0) {
			return "success";
		}
		else {
			return "Permission defined for students " + errlist.substring(1);
		}
	}

    // TODO: CXF and Map need to be changed
	private String updateExternalAssessmentScores(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "externalId", partName = "externalId") @QueryParam("externalId") String externalId,
			@WebParam(name = "scores", partName = "scores") @QueryParam("scores") Map scores) {

		Session s = establishSession(sessionid);
		log.warn("trying to submit grade for" + gradebookUid);

		HashMap newScores = new HashMap();
		Set scoreEids = scores.keySet();

		try {
			for (Iterator i = scoreEids.iterator(); i.hasNext();) {
				String eid = (String) i.next();
				String uid = userDirectoryService.getUserByEid(eid).getId();
				if (scores.get(eid) instanceof String && "".equals((String) scores.get(eid))) {
					newScores.put(uid, null);
				} else {
					newScores.put(uid, scores.get(eid));
				}
			}

			gradebookExternalAssessmentService.updateExternalAssessmentScores(gradebookUid, externalId, newScores);
		} catch (Exception e) {
			log.warn("oops", e);
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

    @WebMethod
    @Path("/removeExternalAssessment")
    @Produces("text/plain")
    @GET
	public String removeExternalAssessment(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "externalId", partName = "externalId") @QueryParam("externalId") String externalId) {

		Session s = establishSession(sessionid);

		try {
			gradebookExternalAssessmentService.removeExternalAssessment(gradebookUid, externalId);
		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

		return "success";
	}

    @WebMethod
    @Path("/getAssignments")
    @Produces("text/plain")
    @GET
	public String getAssignments(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "delim", partName = "delim") @QueryParam("delim") String delim) {

		Session s = establishSession(sessionid);
		String retval = "";

		try {
			List Assignments = gradebookService.getAssignments(gradebookUid);
			for (Iterator iAssignment = Assignments.iterator(); iAssignment.hasNext();) {
				Assignment a = (Assignment) iAssignment.next();

				retval = retval + delim + a.getName();
			}

		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

		if (retval.length() == 0)
			return retval;
		else
			return retval.substring(1);
	}

    @WebMethod
    @Path("/getAssignmentScores")
    @Produces("text/plain")
    @GET
	public String getAssignmentScores(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid) {

		Session s = establishSession(sessionid);

		Document dom = Xml.createDocument();
		Node all = dom.createElement("Assignments");
		dom.appendChild(all);

		try {
			ArrayList<String> azGroups = new ArrayList<String>();
			azGroups.add("/site/" + gradebookUid);

			Set<String> students = authzGroupService.getUsersIsAllowed("section.role.student", azGroups);
			List<User> users = userDirectoryService.getUsers(students);

			Map<String, String> validStudents = new HashMap<String, String>();
			List<String> validStudentUids = new ArrayList<String>();
			for (User u : users) {
				validStudents.put(u.getId(), u.getEid());
				validStudentUids.add(u.getId());
			}

			List<Assignment> assignmentList = gradebookService.getAssignments(gradebookUid);
			for (Assignment a : assignmentList) {
				Element uElement = dom.createElement("Assignment");
				uElement.setAttribute("id", a.getId().toString());
				uElement.setAttribute("name", a.getName());
				uElement.setAttribute("points", a.getPoints().toString());

				List<GradeDefinition> defs = gradebookService.getGradesForStudentsForItem(gradebookUid, a.getId(), validStudentUids);
				for (GradeDefinition gd : defs) {
					Element sElement = dom.createElement("Score");
					sElement.setAttribute("studentUid", gd.getStudentUid());
					sElement.setAttribute("studentEid", validStudents.get(gd.getStudentUid()));
					sElement.setAttribute("grade", gd.getGrade());
					// sElement.setAttribute("comment", gd.getGradeComment());
					uElement.appendChild(sElement);
				}
				all.appendChild(uElement);
			}
			return Xml.writeDocumentToString(dom);

		} catch (Exception e) {
			log.warn("getAssignmentScores failure", e);
			return "failure: " + e.getMessage();
		}
	}

    @WebMethod
    @Path("/isUserAbleToGradeStudent")
    @Produces("text/plain")
    @GET
	public String isUserAbleToGradeStudent(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "studentUid", partName = "studentUid") @QueryParam("studentUid") String studentUid) {

		Session s = establishSession(sessionid);
		boolean retval;

		try {
			retval = gradebookService.isUserAbleToGradeItemForStudent(gradebookUid, null, studentUid);
		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

		if (retval) {
			return "true";
		} else {
			return "false";
		}
	}

    private boolean mapsAreEqual(Map<String, Double> mapA, Map<String, Double> mapB) {

        try{
            for (String k : mapB.keySet())
            {
                log.debug("SakaiGradebook:Comparing the default old value:" + mapA.get(k) + " with actual value:" + mapB.get(k));
                if (!(mapA.get(k).compareTo(mapB.get(k))==0)) {
                    return false;
                }
            }
            for (String y : mapA.keySet())
            {
                if (!mapB.containsKey(y)) {
                    log.debug("SakaiGradebook:Key not found comparing, so they are different:" + !mapB.containsKey(y));
                    return false;
                }
            }
        } catch (NullPointerException np) {
            return false;
        }
        return true;
    }
}
