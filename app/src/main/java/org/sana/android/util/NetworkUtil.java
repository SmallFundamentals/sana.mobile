package org.sana.android.util;

/**
 * Created by Errin on 12/03/2017.
 */
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;

import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.sana.android.db.ChecksumResultsDAO;
import org.sana.android.db.ImageProvider;
import org.sana.android.net.MDSInterface;
import org.sana.android.procedure.ProcedureElement;
import org.sana.net.MDSResult;
import org.sana.net.http.HttpTaskFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static android.R.attr.start;
import static android.R.attr.type;
import static android.support.v7.appcompat.R.id.end;

public class NetworkUtil {

    private static final String GET_CHECKSUMS_URL = "http://blakeyu.com/mds/json/checksums/get/";
    private static final String UPLOAD_INSTRUCTION_URL = "http://blakeyu.com/mds/json/binarychunk/submit/";

    /**
     * Get rolling and md5 checksum from the server
     * @param binUri
     * @return ChecksumResultsDAO
     */
    public static ChecksumResultsDAO getChecksumResult(Uri binUri, int fileSize, String savedProcedureId, String elementId) throws FileNotFoundException, UnsupportedEncodingException {
        ImageProvider imageProvider = new ImageProvider();
        String fileName = imageProvider.buildFilenameFromUri(binUri);
        return getChecksumResult(fileName, fileName, fileSize, savedProcedureId, elementId);
    }

    /**
     * Get rolling and md5 checksum from the server
     * @param localFileName
     * @param remoteFileName
     * @return ChecksumResultsDAO
     */
    public static ChecksumResultsDAO getChecksumResult(String localFileName, String remoteFileName, int fileSize, String savedProcedureId, String elementId)
            throws FileNotFoundException, UnsupportedEncodingException {

        //HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(GET_CHECKSUMS_URL);


        MultipartEntity entity = new MultipartEntity();
        entity.addPart("procedure_guid", new StringBody(savedProcedureId));
        entity.addPart("element_id", new StringBody(elementId));
        entity.addPart("file_size", new StringBody(Integer.toString(fileSize)));

        //execute
        post.setEntity(entity);
        HttpClient client = HttpTaskFactory.CLIENT_FACTORY.produce();

        //MDSResult postResponse = MDSInterface.doPost(c, mUrl, entity);
        HttpResponse httpResponse = null;
        ChecksumResultsDAO result = null;

        try {

            httpResponse = client.execute(post);
            //Log.d(TAG, "postResponses got response code " +  httpResponse.getStatusLine().getStatusCode());

            char buf[] = new char[20560];
            String jsonString = EntityUtils.toString(httpResponse.getEntity());
            //Log.d(TAG, "Received from MDS:" + responseString.length()+" chars");
            Gson gson = new Gson();
            result = gson.fromJson(jsonString, ChecksumResultsDAO.class);

        } catch (IOException e1) {
            //Log.e(TAG, e1.toString());
            e1.printStackTrace();
        } catch (JsonParseException e) {
            //Log.e(TAG, "postResponses(): Error parsing MDS JSON response: "
            //        + e.getMessage());
        }

        //List<NameValuePair> params = new ArrayList<>();
        //params.add(new BasicNameValuePair("name", remoteFileName));
        //params.add(new BasicNameValuePair("size", String.valueOf(fileSize)));

        //try {
            //post.setEntity(new UrlEncodedFormEntity(params));
            //HttpResponse response = httpClient.execute(post);
            //String jsonString = EntityUtils.toString(response.getEntity());
            //result = new Gson().fromJson(jsonString, ChecksumResultsDAO.class);
            //result.setFileName(localFileName);
        //} catch (IOException ex) {
        //    System.out.println(ex);
        //}
        return result;
    }

    /**
     * Extract data from instructions and send it via POST request.
     * @param instructions
     */
    public static void send(List<Object> instructions, String savedProcedureId,
                            String elementId, String binaryGuid, ProcedureElement.ElementType type, int fileSize) throws UnsupportedEncodingException {
        //System.out.println(String.format("-- Sending instructions for '%s' --", fileName));
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i) instanceof ArrayList) {

                List<Byte> data = (List<Byte>) instructions.get(i);
                Byte[] bytes = data.toArray(new Byte[data.size()]);
                byte[] rawBytes = getRawBytes(bytes);
                //rounds up
                int numOfTransmits = (int) Math.ceil(fileSize / 1024);
                for(int j = 0; j < numOfTransmits -1; j++) {
                    int start = j * 1024;
                    int end = ((j+1) * 1024) > fileSize ? fileSize : ((j+1) * 1024);

                    sendBinary(rawBytes, savedProcedureId, elementId, binaryGuid, type, start, end, fileSize);

                }
            }
        }

    }

    /**
     * Send a binary chunk with its index to server
     * @param bytes
     */
    public static void sendBinary(byte[] bytes, String savedProcedureId,
                                  String elementId, String binaryGuid, ProcedureElement.ElementType type, int start, int end, int fileSize) throws UnsupportedEncodingException {

        HttpPost post = new HttpPost(UPLOAD_INSTRUCTION_URL);

        MultipartEntity entity = new MultipartEntity();
        entity.addPart("procedure_guid", new StringBody(savedProcedureId));
        entity.addPart("element_id", new StringBody(elementId));
        entity.addPart("binary_guid", new StringBody(binaryGuid));
        entity.addPart("element_type", new StringBody(type.toString()));
        entity.addPart("file_size", new StringBody(Integer.toString(fileSize)));
        entity.addPart("byte_start", new StringBody(Integer.toString(start)));
        entity.addPart("byte_end", new StringBody(Integer.toString(end)));
        entity.addPart("byte_data", new ByteArrayBody(bytes, type.getFilename()));

        //execute
        post.setEntity(entity);
        HttpClient client = HttpTaskFactory.CLIENT_FACTORY.produce();

        HttpResponse httpResponse = null;

        try {

            httpResponse = client.execute(post);
            //Log.d(TAG, "postResponses got response code " +  httpResponse.getStatusLine().getStatusCode());

            char buf[] = new char[20560];
            //Log.d(TAG, "Received from MDS:" + responseString.length()+" chars");
            //Gson gson = new Gson();
            //result = gson.fromJson(jsonString, ChecksumResultsDAO.class);
            int statusCode = httpResponse.getStatusLine().getStatusCode();


            String responseBody = EntityUtils.toString(httpResponse.getEntity());
            System.out.println(String.format("Status: %d Response: %s", statusCode, responseBody));
        } catch (IOException e1) {
            //Log.e(TAG, e1.toString());
            e1.printStackTrace();
        } catch (JsonParseException e) {
            //Log.e(TAG, "postResponses(): Error parsing MDS JSON response: "
            //        + e.getMessage());
        }

    }

    private static byte[] getRawBytes(Byte[] data) {
        byte[] raw = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            raw[i] = data[i];
        }
        return raw;
    }
}
