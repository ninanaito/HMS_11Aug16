package com.onsemi.hms.controller;

import com.onsemi.hms.dao.ParameterDetailsDAO;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import com.onsemi.hms.dao.WhRequestDAO;
import com.onsemi.hms.model.ParameterDetails;
import com.onsemi.hms.model.WhRequest;
import com.onsemi.hms.model.UserSession;
import com.onsemi.hms.tools.EmailSender;
import com.onsemi.hms.tools.QueryResult;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value = "/wh/whRequest")
@SessionAttributes({"userSession"})
public class WhRequestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhRequestController.class);
    String[] args = {};

    //Delimiters which has to be in the CSV file
    private static final String COMMA_DELIMITER = ",";
    private static final String LINE_SEPARATOR = "\n";

    //File header
    private static final String HEADER = "id,request_type,hardware_type,hardware_id,type,quantity,requested_by,"
            + "requested_date,remarks";

    @Autowired
    private MessageSource messageSource;

    @Autowired
    ServletContext servletContext;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public String whRequest(
            Model model, @ModelAttribute UserSession userSession
    ) {
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        List<WhRequest> whRequestList = whRequestDAO.getWhRequestList();
        String groupId = userSession.getGroup();

        model.addAttribute("whRequestList", whRequestList);
        model.addAttribute("groupId", groupId);

        return "whRequest/whRequest";
    }

    @RequestMapping(value = "/add", method = RequestMethod.GET)
    public String add(Model model, @ModelAttribute UserSession userSession) {

        ParameterDetailsDAO sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> requestType = sDAO.getGroupParameterDetailList("", "006");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> equipmentType = sDAO.getGroupParameterDetailList("", "002");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> mb = sDAO.getGroupParameterDetailList("", "011");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> stencil = sDAO.getGroupParameterDetailList("", "012");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> tray = sDAO.getGroupParameterDetailList("", "013");

        String username = userSession.getFullname();
        model.addAttribute("requestType", requestType);
        model.addAttribute("equipmentType", equipmentType);
        model.addAttribute("mb", mb);
        model.addAttribute("stencil", stencil);
        model.addAttribute("tray", tray);
        model.addAttribute("username", username);
        return "whRequest/add";
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public String save(
            Model model,
            HttpServletRequest request,
            Locale locale,
            RedirectAttributes redirectAttrs,
            @ModelAttribute UserSession userSession,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String equipmentIdMb,
            @RequestParam(required = false) String equipmentIdTray,
            @RequestParam(required = false) String equipmentIdStencil,
            @RequestParam(required = false) String equipmentIdPcb,
            @RequestParam(required = false) String quantity,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) String remarks) {
        WhRequest whRequest = new WhRequest();
        whRequest.setRequestType(requestType);
        whRequest.setEquipmentType(equipmentType);
        if (!quantity.equals("")) {
            whRequest.setQuantity(quantity);
        } else {
            whRequest.setQuantity("1");
        }

        whRequest.setType(type);

        if (!equipmentIdMb.equals("")) {
            whRequest.setEquipmentId(equipmentIdMb);
        } else if (!equipmentIdStencil.equals("")) {
            whRequest.setEquipmentId(equipmentIdStencil);
        } else if (!equipmentIdTray.equals("")) {
            whRequest.setEquipmentId(equipmentIdTray);
        } else if (!equipmentIdPcb.equals("")) {
            whRequest.setEquipmentId(equipmentIdPcb);
        } else {
            whRequest.setEquipmentId(equipmentId);
        }
        whRequest.setRequestedBy(userSession.getFullname());
        whRequest.setRemarks(remarks);
        whRequest.setCreatedBy(userSession.getId());
        whRequest.setStatus("Waiting for Approval");
        whRequest.setFlag("1");
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        QueryResult queryResult = whRequestDAO.insertWhRequest(whRequest);
        args = new String[1];
        args[0] = requestType + " - " + equipmentType;
        if (queryResult.getGeneratedKey().equals("0")) {
            model.addAttribute("error", messageSource.getMessage("general.label.save.error", args, locale));
            model.addAttribute("whRequest", whRequest);
            return "whRequest/add";
        } else {
            redirectAttrs.addFlashAttribute("success", messageSource.getMessage("general.label.save.success", args, locale));

//            only send email to approver if requestor want to ship the item
            if ("Ship".equals(requestType)) {
                LOGGER.info("email will be send to approver");

                EmailSender emailSender = new EmailSender();
                com.onsemi.hms.model.User user = new com.onsemi.hms.model.User();
                user.setFullname(userSession.getFullname());
                emailSender.htmlEmail(
                        servletContext,
                        //                    user name
                        user,
                        //                    to
                        "farhannazri27@yahoo.com",
                        //                    subject
                        "New Hardware Request from HMS",
                        //                    msg
                        "New Hardware Request has been added to HMS. Please go to this link "
                        + "<a href=\"" + request.getScheme() + "://fg79cj-l1:" + request.getServerPort() + request.getContextPath() + "/wh/whRequest/approval/" + queryResult.getGeneratedKey() + "\">HMS</a>"
                        + " for approval process."
                );
            }

//            only create csv file and send email to warehouse if requestor want to retrieve hardware
            if ("Retrieve".equals(requestType)) {

                FileWriter fileWriter = null;

                try {
                    fileWriter = new FileWriter("C:\\test.csv");

                    //Adding the header
                    fileWriter.append(HEADER);
                    //New Line after the header
                    fileWriter.append(LINE_SEPARATOR);

                    //Iterate the empList
                    WhRequestDAO whdao = new WhRequestDAO();
                    WhRequest wh = whdao.getWhRequest(queryResult.getGeneratedKey());

                    fileWriter.append(queryResult.getGeneratedKey());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getRequestType());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getEquipmentType());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getEquipmentId());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getType());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getQuantity());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getRequestedBy());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getRequestedDate());
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(wh.getRemarks());
//    			fileWriter.append(LINE_SEPARATOR);
                    System.out.println("Write to CSV file Succeeded!!!");
                } catch (Exception ee) {
                    ee.printStackTrace();
                } finally {
                    try {
                        fileWriter.close();
                    } catch (IOException ie) {
                        System.out.println("Error occured while closing the fileWriter");
                        ie.printStackTrace();
                    }
                }

//                send email
                LOGGER.info("send email to warehouse");

                EmailSender emailSender = new EmailSender();
                com.onsemi.hms.model.User user = new com.onsemi.hms.model.User();
                user.setFullname(userSession.getFullname());
                emailSender.htmlEmailWithAttachment(
                        servletContext,
                        //                    user name
                        user,
                        //                    to
                        "ama_nina1993@yahoo.com",
                        //                    subject
                        "New Hardware Request from HMS",
                        //                    msg
                        "New Hardware Request has been added to HMS. Please go to this link "
                        + "<a href=\"" + request.getScheme() + "://fg79cj-l1:" + request.getServerPort() + request.getContextPath() + "/wh/whRequest/approval/" + queryResult.getGeneratedKey() + "\">HMS</a>"
                        + " for shipping process."
                );

            }

            return "redirect:/wh/whRequest/edit/" + queryResult.getGeneratedKey();
        }
    }

    @RequestMapping(value = "/edit/{whRequestId}", method = RequestMethod.GET)
    public String edit(
            Model model,
            @PathVariable("whRequestId") String whRequestId
    ) {
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        WhRequest whRequest = whRequestDAO.getWhRequest(whRequestId);

        ParameterDetailsDAO sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> requestType = sDAO.getGroupParameterDetailList(whRequest.getRequestType(), "006");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> equipmentType = sDAO.getGroupParameterDetailList(whRequest.getEquipmentType(), "002");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> mb = sDAO.getGroupParameterDetailList(whRequest.getEquipmentId(), "011");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> stencil = sDAO.getGroupParameterDetailList(whRequest.getEquipmentId(), "012");

        sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> tray = sDAO.getGroupParameterDetailList(whRequest.getEquipmentId(), "013");

        model.addAttribute("requestType", requestType);
        model.addAttribute("equipmentType", equipmentType);
        model.addAttribute("mb", mb);
        model.addAttribute("stencil", stencil);
        model.addAttribute("tray", tray);
        model.addAttribute("whRequest", whRequest);
        return "whRequest/edit";
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public String update(
            Model model,
            Locale locale,
            RedirectAttributes redirectAttrs,
            @ModelAttribute UserSession userSession,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String equipmentIdMb,
            @RequestParam(required = false) String equipmentIdStencil,
            @RequestParam(required = false) String equipmentIdTray,
            @RequestParam(required = false) String equipmentIdPcb,
            @RequestParam(required = false) String quantity,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String flag
    ) {
        WhRequest whRequest = new WhRequest();
        whRequest.setId(id);
        whRequest.setRequestType(requestType);
        if (!quantity.equals("")) {
            whRequest.setQuantity(quantity);
        } else {
            whRequest.setQuantity("1");
        }
        whRequest.setType(type);
        whRequest.setEquipmentType(equipmentType);
        if (!equipmentIdMb.equals("")) {
            whRequest.setEquipmentId(equipmentIdMb);
        } else if (!equipmentIdStencil.equals("")) {
            whRequest.setEquipmentId(equipmentIdStencil);
        } else if (!equipmentIdTray.equals("")) {
            whRequest.setEquipmentId(equipmentIdTray);
        } else if (!equipmentIdPcb.equals("")) {
            whRequest.setEquipmentId(equipmentIdPcb);
        } else {
            whRequest.setEquipmentId(equipmentId);
        }
        whRequest.setRemarks(remarks);
        whRequest.setModifiedBy(userSession.getId());
        whRequest.setFlag(flag);
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        QueryResult queryResult = whRequestDAO.updateWhRequest(whRequest);
        args = new String[1];
        args[0] = requestType + " - " + equipmentType;
        if (queryResult.getResult() == 1) {
            redirectAttrs.addFlashAttribute("success", messageSource.getMessage("general.label.update.success", args, locale));
        } else {
            redirectAttrs.addFlashAttribute("error", messageSource.getMessage("general.label.update.error", args, locale));
        }
        return "redirect:/wh/whRequest/edit/" + id;
    }

    @RequestMapping(value = "/delete/{whRequestId}", method = RequestMethod.GET)
    public String delete(
            Model model,
            Locale locale,
            RedirectAttributes redirectAttrs,
            @PathVariable("whRequestId") String whRequestId
    ) {
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        WhRequest whRequest = whRequestDAO.getWhRequest(whRequestId);
        whRequestDAO = new WhRequestDAO();
        QueryResult queryResult = whRequestDAO.deleteWhRequest(whRequestId);
        args = new String[1];
        args[0] = whRequest.getRequestType() + " - " + whRequest.getEquipmentType();
        if (queryResult.getResult() == 1) {
            redirectAttrs.addFlashAttribute("success", messageSource.getMessage("general.label.delete.success", args, locale));
        } else {
            redirectAttrs.addFlashAttribute("error", messageSource.getMessage("general.label.delete.error", args, locale));
        }
        return "redirect:/wh/whRequest";
    }

    @RequestMapping(value = "/view/{whRequestId}", method = RequestMethod.GET)
    public String view(
            Model model,
            HttpServletRequest request,
            @PathVariable("whRequestId") String whRequestId
    ) throws UnsupportedEncodingException {
        String pdfUrl = URLEncoder.encode(request.getContextPath() + "/wh/whRequest/viewWhRequestPdf/" + whRequestId, "UTF-8");
        String backUrl = servletContext.getContextPath() + "/wh/whRequest";
        model.addAttribute("pdfUrl", pdfUrl);
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("pageTitle", "Warehouse Management - Hardware Request");
        return "pdf/viewer";
    }

    @RequestMapping(value = "/viewWhRequestPdf/{whRequestId}", method = RequestMethod.GET)
    public ModelAndView viewWhRequestPdf(
            Model model,
            @PathVariable("whRequestId") String whRequestId
    ) {
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        WhRequest whRequest = whRequestDAO.getWhRequest(whRequestId);
        return new ModelAndView("whRequestPdf", "whRequest", whRequest);
    }

    @RequestMapping(value = "/approval/{whRequestId}", method = RequestMethod.GET)
    public String approval(
            Model model,
            @PathVariable("whRequestId") String whRequestId
    ) {
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        WhRequest whRequest = whRequestDAO.getWhRequest(whRequestId);

        ParameterDetailsDAO sDAO = new ParameterDetailsDAO();
        List<ParameterDetails> approvalStatus = sDAO.getGroupParameterDetailList(whRequest.getFinalApprovedStatus(), "007");
        model.addAttribute("whRequest", whRequest);
        model.addAttribute("approvalStatus", approvalStatus);
        return "whRequest/approval";
    }

    @RequestMapping(value = "/approvalupdate", method = RequestMethod.POST)
    public String approvalupdate(
            Model model,
            Locale locale,
            HttpServletRequest request,
            RedirectAttributes redirectAttrs,
            @ModelAttribute UserSession userSession,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) String finalApprovedStatus,
            @RequestParam(required = false) String finalApprovedDate,
            @RequestParam(required = false) String remarksApprover,
            @RequestParam(required = false) String modifiedBy,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String flag
    ) {
        WhRequest whRequest = new WhRequest();
        whRequest.setId(id);
        whRequest.setFinalApprovedStatus(finalApprovedStatus);
        whRequest.setFinalApprovedBy(userSession.getFullname());
        whRequest.setRemarksApprover(remarksApprover);
        whRequest.setStatus(finalApprovedStatus);
        WhRequestDAO whRequestDAO = new WhRequestDAO();
        QueryResult queryResult = whRequestDAO.updateWhRequestForApproval(whRequest);
        args = new String[1];
        args[0] = requestType + " - " + equipmentType;
        if (queryResult.getResult() == 1) {
            redirectAttrs.addFlashAttribute("success", messageSource.getMessage("general.label.update.success", args, locale));

            EmailSender emailSender = new EmailSender();
            whRequestDAO = new WhRequestDAO();
            WhRequest whRequest1 = whRequestDAO.getWhRequest(id);
            String fullname = whRequest1.getRequestedBy();
            com.onsemi.hms.model.User user = new com.onsemi.hms.model.User();
            user.setFullname(fullname);

            emailSender.htmlEmail(
                    servletContext,
                    //                    user name
                    user,
                    //                    to
                    "farhannazri27@yahoo.com",
                    //                    subject
                    "Approval Status for New Hardware Request from HMS",
                    //                    msg
                    "Approval status for New Hardware Request has been made. Please go to this link "
                    + "<a href=\"" + request.getScheme() + "://fg79cj-l1:" + request.getServerPort() + request.getContextPath() + "/wh/whRequest/edit/" + id + "\">HMS</a>"
                    + " for approval status checking."
            );

        } else {
            redirectAttrs.addFlashAttribute("error", messageSource.getMessage("general.label.update.error", args, locale));
        }
        return "redirect:/wh/whRequest";
    }
}
