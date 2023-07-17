package org.sakaiproject.webservices;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.gradebook.Gradebook;
import org.sakaiproject.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Brock
 * <p/>
 * A set of custom web services for Brock Sakai
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
public class Brock extends AbstractWebService {
	private static final Log LOG = LogFactory.getLog(Brock.class);

	@WebMethod
	@Path("/brockGetSiteIds")
	@Produces("text/xml")
	@GET
	public String brockGetSiteIds(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "criteria", partName = "criteria") @QueryParam("criteria") String criteria) 
	{
	  Session session = establishSession(sessionId);
	  String returnValue = "";

	  try {
	    Document dom = Xml.createDocument();
	    Node siteList = dom.createElement("sites");
	    dom.appendChild(siteList);

	    List<Site> siteIds = siteService.getSites(org.sakaiproject.site.api.SiteService.SelectionType.ANY, null, criteria, null, SortType.TITLE_ASC, null);

	    for (int i = 0; i < siteIds.size(); i++) {
	            Boolean gradebookExists = false;
	            String siteTitle = siteIds.get(i).getTitle();
	            try {
	                gradebookExists = gradebookService.isGradebookDefined(siteTitle);
	            } catch(Exception e) {
	            	// No gradebook defined for site. Ignore.
	            }

	      Node item = dom.createElement("site");
	      siteList.appendChild(item);
	      item.appendChild( dom.createTextNode(siteTitle + (gradebookExists ? " *" : "")) );
	    }
	    returnValue = Xml.writeDocumentToString(dom);
	  }
	  catch(Exception e) {
	    return e.getClass().getName() + " : " + e.getMessage();
	  }
	  return returnValue;
	}

	@WebMethod
	@Path("/getSitesForProvider")
	@Produces("text/xml")
	@GET
	public String getSitesForProvider(			
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "providerId", partName = "providerId") @QueryParam("providerId") String providerId) 
	{

		Session session = establishSession(sessionId);
	
		if (!securityService.isSuperUser()) {
			LOG.warn("WS getSitesForProvider(): Permission denied. Restricted to super users.");
			return "FAILURE: getSitesForProvider(): Permission denied. Restricted to super users.";
		}

		Document dom = Xml.createDocument();
		Node course = dom.createElement("course");
		dom.appendChild(course);

		try {
			// First need to try to convert the providerId into a real site
			List<Map<String, String>> providerList = dbRead("SELECT realm_id FROM SAKAI_REALM WHERE PROVIDER_ID LIKE ?",
				new String[]{ "%" + providerId + "%" }, new String[]{"realm_id"});

			for (Map<String, String> map : providerList) {
				String realmId = map.get("realm_id");

				if(!StringUtils.contains(realmId, "/group/")) {
					String siteId = StringUtils.replace(realmId, "/site/", "");
					Boolean gradebookExists = false;
					try {
						gradebookExists = gradebookService.isGradebookDefined(siteId);
					} catch(Exception e) {
						// No gradebook defined for site. Ignore.
					}

					Node siteT = dom.createElement("site");
					course.appendChild(siteT);
					siteT.appendChild(dom.createTextNode(siteId + (gradebookExists ? " *" : "")));
				}
			}

			return Xml.writeDocumentToString(dom);
		} catch (Exception e) {
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
	}

	@WebMethod
	@Path("/getCourseGrades2")
	@Produces("text/xml")
	@GET
	public String getCourseGrades2(			
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "providerId", partName = "providerId") @QueryParam("providerId") String providerId) 
	{

		Session session = establishSession(sessionId);
	
		if (!securityService.isSuperUser()) {
			LOG.warn("WS getCourseGrades2(): Permission denied. Restricted to super users.");
			return "FAILURE: getCourseGrades2(): Permission denied. Restricted to super users.";
		}

		String gradeResult = "";
		String runningLog = "";
		Site site = null;
		String siteId = "";

		try {
			// First need to try to convert the providerId into a real site
			List<Map<String, String>> providerList = dbRead("SELECT realm_id FROM SAKAI_REALM WHERE PROVIDER_ID LIKE ?",
				new String[]{ "%" + providerId + "%" }, new String[]{"realm_id"});

			Set<String> sites = new HashSet<String>();
			for(Map<String, String> map : providerList){
				String realmId = map.get("realm_id");

				if(!StringUtils.contains(realmId, "/group/")) {
					siteId = StringUtils.replace(realmId, "/site/", "");
					sites.add(siteId);
				}
			}
			
			// We have to bail if there are multiple sites or no sites found
			if (sites.isEmpty()) {
				return "error: no sites associated with that providerId";
			}
			else if (sites.size() > 1) {
				return "error: more than on site is associated that providerId";
			}

			Gradebook gb = (Gradebook) gradebookService.getGradebook(siteId);

			site = siteService.getSite(siteId);
			Set<Member> members = site.getMembers();
			
			Long gbId = gb.getId();
			Integer catType = gb.getCategory_type();
			Integer gradeType = gb.getGrade_type();

	        Document dom = Xml.createDocument();
	        Node course = dom.createElement("course");
	        dom.appendChild(course);

			Node siteT = dom.createElement("site");
			course.appendChild(siteT);
			siteT.appendChild(dom.createTextNode(siteId));

//			Node category = dom.createElement("category_type");
//			course.appendChild(category);
//			category.appendChild(dom.createTextNode(String.valueOf(catType)));

//			Node gradeT = dom.createElement("grade_type");
//			course.appendChild(gradeT);
//			gradeT.appendChild(dom.createTextNode(String.valueOf(gradeType)));

			String GB_ASSIGNMENTS_SQL = "";
			List<Map<String, String>> returnList = null;

			if( catType == 1 ) {
				GB_ASSIGNMENTS_SQL = "SELECT go.ID,go.NAME,go.POINTS_POSSIBLE from GB_GRADABLE_OBJECT_T go LEFT JOIN GB_GRADEBOOK_T gb ON gb.ID=go.GRADEBOOK_ID where go.OBJECT_TYPE_ID=1 AND go.NOT_COUNTED!=1 and go.REMOVED!=1 AND gb.NAME=?";
				returnList = dbRead(GB_ASSIGNMENTS_SQL, new String[]{siteId}, new String[]{"ID", "NAME", "POINTS_POSSIBLE"});
			}
			else {
				GB_ASSIGNMENTS_SQL = "SELECT go.ID,go.NAME,go.POINTS_POSSIBLE,go.CATEGORY_ID FROM GB_GRADABLE_OBJECT_T go LEFT JOIN GB_GRADEBOOK_T gb ON gb.ID=go.GRADEBOOK_ID where go.OBJECT_TYPE_ID=1 AND go.NOT_COUNTED!=1 and go.REMOVED!=1 AND gb.NAME=?";
				returnList = dbRead(GB_ASSIGNMENTS_SQL, new String[]{siteId}, new String[]{"ID", "NAME", "POINTS_POSSIBLE", "CATEGORY_ID"});
			}

			String GB_CATEGORY_SQL = "SELECT WEIGHT,DROP_LOWEST,DROP_HIGHEST,KEEP_HIGHEST FROM GB_CATEGORY_T WHERE ID=?";
			String GB_OVERRIDE_SQL = "SELECT PERCENT FROM GB_GRADE_RECORD_T gr LEFT JOIN GB_GRADABLE_OBJECT_T go ON gr.GRADABLE_OBJECT_ID=go.ID LEFT JOIN GB_GRADEBOOK_T gb ON gb.ID=go.GRADEBOOK_ID LEFT JOIN GB_GRADE_MAP_T gm on gm.ID=gb.SELECTED_GRADE_MAPPING_ID LEFT JOIN GB_GRADING_SCALE_PERCENTS_T gsp ON gsp.GRADING_SCALE_ID=gm.GB_GRADING_SCALE_T WHERE gsp.LETTER_GRADE=gr.ENTERED_GRADE AND go.OBJECT_TYPE_ID=2 AND go.GRADEBOOK_ID=? AND STUDENT_ID=?";

			for(Member member: members) {
				if( member.isActive() ) {
					Double finalGrade = 0.0;
					runningLog = "";

					Map<Long, Double> categoryPossible = new HashMap<Long, Double>();
					Map<Long, Double> categorySingleScore = new HashMap<Long, Double>();

					String studentId = member.getUserId();
					String studentRole = member.getRole().getId();

					if( studentRole.equals("Student") ) {
						runningLog += "Doing lookup of enterGrade using gbId:" + String.valueOf(gbId) + " and studentId:" + studentId + "\n";

						List<Map<String, String>> enteredGrade = dbRead(GB_OVERRIDE_SQL, new String[]{String.valueOf(gbId), studentId}, new String[]{"PERCENT"});
						if( enteredGrade.size() > 0 ) {
							finalGrade = Double.parseDouble(enteredGrade.get(0).get("PERCENT"));
							runningLog += "finalGrade: " + finalGrade + " (forced)\n";
						}
						else {
							Double totalPoints = 0.0, totalPossible = 0.0;
							Map<Long, List<Double>> categoryScores = new HashMap<Long, List<Double>>();

							for(Map<String, String> map : returnList) {
								Long assignmentId = Long.parseLong(map.get("ID"));
								String assignmentScoreString = gradebookService.getAssignmentScoreString(siteId, assignmentId, studentId);
								runningLog += "assignment: " + String.valueOf(assignmentId) + "; assignmentScore: " + assignmentScoreString + "; ";
								Double assignmentScore = StringUtils.isNotBlank(assignmentScoreString) ? Double.parseDouble(assignmentScoreString) : null;

								if( catType != 3 ) {
									if( assignmentScore != null ) {
										totalPossible += Double.parseDouble(map.get("POINTS_POSSIBLE"));
										totalPoints += assignmentScore;
										runningLog += "pointsPossible: " + map.get("POINTS_POSSIBLE");
									}
									runningLog += "\n";
								}
								else {
									Long categoryId = Long.parseLong(map.get("CATEGORY_ID"));
									Double pointsPossible = Double.parseDouble(map.get("POINTS_POSSIBLE"));

									runningLog += "categoryId: " + String.valueOf(categoryId) + "; pointsPossible: " + String.valueOf(pointsPossible) + "\n";

									categorySingleScore.put(categoryId, pointsPossible);
									if( !categoryScores.containsKey(categoryId) ) {
										categoryScores.put(categoryId, new ArrayList<Double>());
									}

									if( assignmentScore != null ) {
										if( categoryPossible.containsKey(categoryId) ) {
											categoryPossible.put(categoryId, categoryPossible.get(categoryId) + pointsPossible);
										}
										else {
											categoryPossible.put(categoryId, pointsPossible);
										}
										categoryScores.get(categoryId).add(assignmentScore == null ? 0 : assignmentScore);
									}
								}
							}

							if( catType == 3 ) {
								runningLog += "\n";

								for(Long catId: categoryScores.keySet()) {
									List<Map<String, String>> cats = dbRead(GB_CATEGORY_SQL, new String[]{String.valueOf(catId)}, new String[]{"WEIGHT", "DROP_LOWEST", "DROP_HIGHEST", "KEEP_HIGHEST"});
									Map<String, String> thisCat = cats.get(0);

									Double weight = Double.parseDouble(thisCat.get("WEIGHT"));

									runningLog += "catId: " + String.valueOf(catId) + "; weight: " + String.valueOf(weight) + "; drop_lowest: " + thisCat.get("DROP_LOWEST") + "; ";

									List<Double> myScores = categoryScores.get(catId);
									if( myScores != null ) {
										if( myScores.size() > 0 ) {
											Collections.sort(myScores);
											Collections.reverse(myScores);

											Double catPossible = categoryPossible.get(catId);
											Double dropped = 0.0;

											if( thisCat.get("KEEP_HIGHEST").equals("1") ) {
												totalPoints += (weight != null ? myScores.get(0) * weight : myScores.get(0));
												catPossible = categorySingleScore.get(catId);
												runningLog += "kept highest: " + String.valueOf(dropped) + "; categorySingleScore: " + categorySingleScore.get(catId) + "; catPossible: " + catPossible + "; ";
											}
											else {
												if( thisCat.get("DROP_HIGHEST").equals("1") ) {
													dropped = myScores.get(0);
													myScores.remove(0);
													catPossible -= categorySingleScore.get(catId);
													runningLog += "dropped highest: " + String.valueOf(dropped) + "; categorySingleScore: " + categorySingleScore.get(catId) + "; catPossible: " + catPossible + "; ";
												}
												if( thisCat.get("DROP_LOWEST").equals("1") ) {
													dropped = myScores.get(myScores.size() - 1);
													myScores.remove(myScores.size() - 1);
													catPossible -= categorySingleScore.get(catId);
													runningLog += "dropped lowest: " + String.valueOf(dropped) + "; categorySingleScore: " + categorySingleScore.get(catId) + "; catPossible: " + catPossible + "; ";
												}

												Double catScore = 0.0;
												for(Double s: myScores) {
													catScore += s;
												}

												Double catPercentage = (catScore*100.0)/catPossible;
												Double catPoints = catPercentage * (weight != null ? weight : 1);

												if( categoryPossible.get(catId) != null ) {
													totalPossible += weight * 100;

												}
												totalPoints += catPoints;
												runningLog += "catScore: " + String.valueOf(catScore) + "; categoryPossible: " + String.valueOf(categoryPossible.get(catId)) + "; catPercentage: " + String.valueOf(catPercentage) + "; catPoints: " + String.valueOf(catPoints) + "; totalPoints: " + String.valueOf(totalPoints) + "\n";
											}
										}
										else {
											runningLog += "catScore: 0; categoryPossible: " + String.valueOf(categoryPossible.get(catId)) + "; catPercentage: 0; totalPoints: " + String.valueOf(totalPoints) + "\n";
										}
									}
								}
							}	

							finalGrade = (totalPoints*100.0)/totalPossible;

							runningLog += "totalPoints: " + String.valueOf(totalPoints) + "; totalPossible: " + String.valueOf(totalPossible) + "; finalGrade: " + String.valueOf(finalGrade) + "\n";
						}

						Long roundedGrade = Math.round(finalGrade);

						Node studentT = dom.createElement("student");
						course.appendChild(studentT);

						// Node idT = dom.createElement("id");
						// studentT.appendChild(idT);
						// idT.appendChild(dom.createTextNode(studentId));

						Node eidT = dom.createElement("student_id");
						studentT.appendChild(eidT);
						eidT.appendChild(dom.createTextNode(member.getUserEid()));

						// Node nameT = dom.createElement("display_name");
						// studentT.appendChild(nameT);
						// nameT.appendChild(dom.createTextNode(member.getUserDisplayId()));

						// Node scoreT = dom.createElement("total_points");
						// studentT.appendChild(scoreT);
						// scoreT.appendChild(dom.createTextNode(String.valueOf(totalPoints)));

						// Node possibleScoreT = dom.createElement("possible_points");
						// studentT.appendChild(possibleScoreT);
						// possibleScoreT.appendChild(dom.createTextNode(String.valueOf(totalPossible)));

						Node percentageT = dom.createElement("percentage");
						studentT.appendChild(percentageT);
						percentageT.appendChild(dom.createTextNode(String.valueOf(roundedGrade)));

						// Node logT = dom.createElement("log");
						// studentT.appendChild(logT);
						// logT.appendChild(dom.createTextNode(runningLog));
					}
				}
			}

			gradeResult = Xml.writeDocumentToString(dom);
		} catch(Exception e) {
			e.printStackTrace();
			return "error: " + runningLog;
		}

		return gradeResult;
	}

	@WebMethod(exclude = true)
	private List<Map<String, String>> dbRead(String SQL, String[] params, String[] cols){
		List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();	
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try{
			connection = sqlService.borrowConnection();
			ps = connection.prepareStatement(SQL);
			int i = 1;
			for(String param : params){
				ps.setString(i, param);
				i++;
			}
			rs = ps.executeQuery();

			if(rs != null){
				while(rs.next()){
					Map<String, String> resultMap = new HashMap<String, String>();
					for(String col : cols){
						resultMap.put(col, rs.getString(col));
					}
					returnList.add(resultMap);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(ps != null){
				try{
					ps.close();
				}catch(Exception e){}
			}
			if(rs != null){
				try{
					rs.close();
				}catch(Exception e){}
			}
			sqlService.returnConnection(connection);
		}

		return returnList;
	}
}
