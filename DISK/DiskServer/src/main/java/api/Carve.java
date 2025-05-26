package api;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import data.fcData.DiskItem;
import data.fcData.Hat;
import data.feipData.Service;
import handlers.DiskHandler;
import handlers.Handler;
import server.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;
import config.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;

@WebServlet(name = ApipApiNames.Carve, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.Carve)
public class Carve extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private final DiskHandler diskHandler = (DiskHandler) Initiator.settings.getHandler(Handler.HandlerType.DISK);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        replier.setCode(CodeMessage.Code1017MethodNotAvailable);
        replier.setMessage(CodeMessage.Msg1017MethodNotAvailable);
        response.getWriter().write(replier.toNiceJson());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        AuthType authType = AuthType.FC_SIGN_URL;
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);

        //Do request
        InputStream inputStream = request.getInputStream();
        Hat hat = diskHandler.put(inputStream);

        Map<String,String> dataMap = new HashMap<>();
        dataMap.put("did", hat.getId());

        //Update item info into ES
        String result = updateCarveDataInfoToEs(hat, settings);
        dataMap.put("result", result);
        replier.reply0SuccessHttp(dataMap,response);

    }

//    @NotNull
//    @SuppressWarnings("UnstableApiUsage")
//    public static Result hashAndSaveFile(HttpServletRequest request) throws IOException {
//        InputStream inputStream = request.getInputStream();
//        String tempFileName = FileUtils.getTempFileName();
//        OutputStream outputStream = new FileOutputStream(tempFileName);
//        HashFunction hashFunction = Hashing.sha256();
//        Hasher hasher = hashFunction.newHasher();
//        // Adjust buffer size as per your requirement
//        byte[] buffer = new byte[8192];
//        int bytesRead;
//        long bytesLength = 0;
//        while ((bytesRead = inputStream.read(buffer)) != -1) {
//            // Write the bytes read from the request input stream to the output stream
//            outputStream.write(buffer, 0, bytesRead);
//            hasher.putBytes(buffer, 0, bytesRead);
//            bytesLength +=bytesRead;
//        }
//
//        String did = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));
//        String subDir = getSubPathForDisk(did);
//        String path = diskDir +subDir;
//
//        File file = new File(path,did);
//        if(!file.exists() || Boolean.FALSE.equals(checkFileOfFreeDisk(path, did))) {
//            try {
//                Path source = Paths.get(tempFileName);
//                Path target = Paths.get(path, did);
//                Files.createDirectories(target.getParent());
//                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
//            } catch (IOException e) {
//                System.err.println("Error moving file: " + e.getMessage());
//            }
//        }
//        // Close the streams
//        outputStream.close();
//        inputStream.close();
//        return new Result(bytesLength, did);
//    }
//
//    private record Result(long bytesLength, String did) {
//    }

    public static String updateCarveDataInfoToEs(Hat hat, Settings settings) {
        long saveDate = System.currentTimeMillis();
        Long expire = null;
        DiskItem diskItem = new DiskItem(hat.getId(), saveDate,expire,hat.getSize());
        ElasticsearchClient esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);

        try {
            IndexResponse result = esClient.index(i -> i.index(Settings.addSidBriefToName(settings.getSid(), DATA)).id(hat.getId()).document(diskItem));
            if(result==null){
                System.out.println("Failed to updateCarveDataInfoToEs");
                return null;
            }
            return result.result().jsonValue();
        } catch (IOException e) {
            System.out.println("Failed to updateCarveDataInfoToEs:"+e.getMessage());
            return null;
        }
    }
}
