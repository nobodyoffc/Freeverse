package api;

import constants.FieldNames;
import fcData.DiskItem;
import tools.RedisTools;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import server.ApipApiNames;
import constants.CodeMessage;
import crypto.Hash;
import fcData.ReplyBody;
import initial.Initiator;
import tools.FileTools;
import tools.Hex;
import tools.http.AuthType;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;
import appTools.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static initial.Initiator.esClient;
import static tools.FileTools.checkFileOfFreeDisk;
import static tools.FileTools.getSubPathForDisk;
import static appTools.Settings.addSidBriefToName;
import static startManager.StartDiskManager.STORAGE_DIR;

@WebServlet(name = ApipApiNames.Carve, value = "/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.Carve)
public class Carve extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(Initiator.settings);
        replier.setCode(CodeMessage.Code1017MethodNotAvailable);
        replier.setMessage(CodeMessage.Msg1017MethodNotAvailable);
        response.getWriter().write(replier.toNiceJson());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(Initiator.settings);

        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }

            //Do request
            Result DidAndLength = hashAndSaveFile(request);

            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("did", DidAndLength.did());

            Double price = RedisTools.readHashDouble(jedis, addSidBriefToName(Initiator.sid, PARAMS), FieldNames.PRICE_PER_K_BYTES_CARVE);
            replier.reply0SuccessHttp(dataMap,response);

            //Update item info into ES
            updateCarveDataInfoToEs(DidAndLength.bytesLength(), DidAndLength.did());
        }
    }

    @NotNull
    @SuppressWarnings("UnstableApiUsage")
    public static Result hashAndSaveFile(HttpServletRequest request) throws IOException {
        InputStream inputStream = request.getInputStream();
        String tempFileName = FileTools.getTempFileName();
        OutputStream outputStream = new FileOutputStream(tempFileName);
        HashFunction hashFunction = Hashing.sha256();
        Hasher hasher = hashFunction.newHasher();
        // Adjust buffer size as per your requirement
        byte[] buffer = new byte[8192];
        int bytesRead;
        long bytesLength = 0;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            // Write the bytes read from the request input stream to the output stream
            outputStream.write(buffer, 0, bytesRead);
            hasher.putBytes(buffer, 0, bytesRead);
            bytesLength +=bytesRead;
        }

        String did = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));
        String subDir = getSubPathForDisk(did);
        String path = STORAGE_DIR +subDir;

        File file = new File(path,did);
        if(!file.exists() || Boolean.FALSE.equals(checkFileOfFreeDisk(path, did))) {
            try {
                Path source = Paths.get(tempFileName);
                Path target = Paths.get(path, did);
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Error moving file: " + e.getMessage());
            }
        }
        // Close the streams
        outputStream.close();
        inputStream.close();
        return new Result(bytesLength, did);
    }

    private record Result(long bytesLength, String did) {
    }

    public static void updateCarveDataInfoToEs(long bytesLength, String did) throws IOException {
        long saveDate = System.currentTimeMillis();
        Long expire = null;
        DiskItem diskItem = new DiskItem(did, saveDate,expire, bytesLength);
        esClient.index(i->i.index(Settings.addSidBriefToName(Initiator.sid,DATA)).id(did).document(diskItem));
    }
}
