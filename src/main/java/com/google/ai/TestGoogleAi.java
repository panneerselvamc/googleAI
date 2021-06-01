package com.google.ai;

import com.cratosys.data.model.AiLineItems;
import com.cratosys.data.model.DocumentType;
import com.cratosys.data.model.Invoice;
import com.google.cloud.documentai.v1beta2.Document;
import com.google.cloud.documentai.v1beta2.DocumentUnderstandingServiceClient;
import com.google.cloud.documentai.v1beta2.InputConfig;
import com.google.cloud.documentai.v1beta2.ProcessDocumentRequest;
import com.google.cloud.documentai.v1beta3.ProcessRequest;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TestGoogleAi {


    String projectId = "visionapiproject-281416";
    String location = "us";

    //v3
    String v3projectId = "visionapiproject-281416";
    String v3location = "us"; // Format is "us" or "eu".
    String v3processerId = "your-processor-id";
    String v3filePath = "path/to/input/file.pdf";
//    processDocument(projectId, location, processerId, filePath);

public void test() throws IOException {

Invoice invoice=new Invoice();
    String parent = String.format("projects/%s/locations/%s", projectId, location);
    DocumentUnderstandingServiceClient documentUnderstandingServiceClient = DocumentUnderstandingServiceClient.create();

    Path path = Paths.get("/home/panneer/work/crato_google_ai/google_ai/ai/src/main/resources/data/sample3.pdf");
    byte[] data = Files.readAllBytes(path);
    InputConfig config = InputConfig.newBuilder().setContents(ByteString.copyFrom(data)).setMimeType("application/pdf").build();
    ProcessDocumentRequest request = ProcessDocumentRequest.newBuilder().setInputConfig(config).setDocumentType("invoice").setParent(parent).build();
    Document response = documentUnderstandingServiceClient.processDocument(request);
    Invoice invoice1=mapAIResponseToInvoice(invoice, response);
    System.out.println(invoice1.getAmount());

  //V3

    System.out.println(invoice1.getLineItems().get(0).getAmount());
    System.out.println(invoice1.getLineItems().get(0).getDescription());
}
public Invoice mapAIResponseToInvoice(Invoice invoice,Document document){

    DocumentType docNonPo = new DocumentType("Non PO Invoice", true);
    DocumentType docPo = new DocumentType("PO Invoice", false);
    invoice.setDocumentType(docNonPo);
    invoice.setPoNumber("");

    //Entites Mapping
    for (Document.Entity entity : document.getEntitiesList()) {
        if (entity.getType().equals("total_amount"))
            invoice.setTotalAmount(Double.valueOf(entity.getMentionText().replaceAll("[^0-9.]", "")));
        else if (entity.getType().equals("total_tax_amount"))
            invoice.setTotaSalesTaxAmount(Double.valueOf(entity.getMentionText().replaceAll("[^0-9.]", "")));
        else if (entity.getType().equals("supplier_name"))
            invoice.setAiVendorName(entity.getMentionText());
        else if (entity.getType().equals("supplier_address"))
            invoice.setVendorAddress(entity.getMentionText());
        else if (entity.getType().equals("receiver_name"))
            invoice.setClientName(entity.getMentionText());
        else if (entity.getType().equals("receiver_address"))
            invoice.setClientLocation(entity.getMentionText());
        else if (entity.getType().equals("delivery_date"))
            invoice.setShippingDate(getDateTime(entity.getMentionText()));
        else if (entity.getType().equals("due_date"))
            invoice.setDueDate(getDateTime(entity.getMentionText()));
        else if (entity.getType().equals("invoice_date"))
            invoice.setInvoiceDate(getDateTime(entity.getMentionText()));
        else if (entity.getType().equals("invoice_id"))
            invoice.setInvoiceNumber(entity.getMentionText());
        else if (entity.getType().equals("purchase_order")) {
            invoice.setPoNumber(entity.getMentionText());
            invoice.setDocumentType(docPo);
        }
    }

    String text = document.getText();
    List<AiLineItems> lineItems = new ArrayList<>(0);

    HashMap<String, Integer> subjectIdVerifier = new HashMap<>();
    int arrayListIndex = 0;
    document.getEntities(0);
    //Entity Relation Mapping
    for (Document.EntityRelation entityRelation : document.getEntityRelationsList()) {

        if (entityRelation.getObjectId().equals(document.getEntities(Integer.parseInt(entityRelation.getObjectId())).getMentionId())) {

            // Object Creation
            if (subjectIdVerifier.containsKey(entityRelation.getSubjectId())) {
                arrayListIndex = subjectIdVerifier.get(entityRelation.getSubjectId());
            } else {
                subjectIdVerifier.put(entityRelation.getSubjectId(), lineItems.size());
                lineItems.add(new AiLineItems());
                arrayListIndex = lineItems.size() - 1;
            }
            String value = getText(document.getEntities(Integer.parseInt(entityRelation.getObjectId())), text);
            if (entityRelation.getRelation().equals("line_item/quantity"))
                lineItems.get(arrayListIndex).setQuantity(value);

            else if (entityRelation.getRelation().equals("line_item/unit"))
                lineItems.get(arrayListIndex).setUnit(value);

            else if (entityRelation.getRelation().equals("line_item/product_code"))
                lineItems.get(arrayListIndex).setProduct_code(value);

            else if (entityRelation.getRelation().equals("line_item/description"))
                lineItems.get(arrayListIndex).setDescription(value);

            else if (entityRelation.getRelation().equals("line_item/amount")){
                lineItems.get(arrayListIndex).setAmount(value);
            }


            else if (entityRelation.getRelation().equals("line_item/unit_price"))
                lineItems.get(arrayListIndex).setUnit_price(value);

        }
    }
    Double subTotal = 0d;
    Double shippingCharges = 0d;
    Double tax = 0d;
    if (invoice.getTotalAmount() != null)
        subTotal = invoice.getTotalAmount();
    if (invoice.getTotaSalesTaxAmount() != null)
        tax = invoice.getTotaSalesTaxAmount();
    Map<String, Double> map = new HashMap<String, Double>();
    if (invoice.getTotalAmount() != null && invoice.getTotalAmount() > 0 && invoice.getTotaSalesTaxAmount() != null && invoice.getTotaSalesTaxAmount() > 0) {
        subTotal = invoice.getTotalAmount() - invoice.getTotaSalesTaxAmount();
    }
    //
    if (invoice.getTotalAmount() != null && invoice.getTotalAmount() == 0d && subTotal == 0d) {
        tax = 0d;
    }
    map.put("Sub Total", subTotal);
    map.put("Shipping", shippingCharges);
    map.put("Tax", tax);
    invoice.setAmount(map);
    invoice.setLineItems(lineItems);
    invoice.setInvoiceLineItems(lineItems);
    return invoice;
}

    private String getText(Document.Entity entity, String text) {
        int startIdx = (int) entity.getTextAnchor().getTextSegments(0).getStartIndex();
        int endIdx = (int) entity.getTextAnchor().getTextSegments(0).getEndIndex();
        return text.substring(startIdx, endIdx);
    }

    public LocalDateTime getDateTime(String date) {
        try {
            String pattern = "MM/dd/yy";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDate localDate = LocalDate.parse(date, formatter);
            return localDate.atStartOfDay();
        } catch (Exception e) {
            System.out.println("ERROR WHILE PROCESSING DATE (STRING TO LOCAL DATE CONVERSION) IN DOCUMENT PROCESSOR");
            return null;
        }
    }
}
