package APIP2V2_Blockchain;

import constants.ApiNames;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.BLOCK_ID;

@WebServlet(name = ApiNames.BlockSearch, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.BlockSearch)
public class BlockSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        FcdslRequestHandler.doBlockInfoRequest(Initiator.sid,false,BLOCK_ID, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        FcdslRequestHandler.doBlockInfoRequest(Initiator.sid,false,BLOCK_ID,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
}