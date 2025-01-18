package clients;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import configure.ApiAccount;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import crypto.Decryptor;
import crypto.old.EccAes256K1P7;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.*;

public class EsClientMaker {

    private static final Logger log = LoggerFactory.getLogger(EsClientMaker.class);
    public ElasticsearchClient esClient;
    RestClient restClient;
    RestClientTransport transport;

    public ElasticsearchClient getSimpleEsClient() throws IOException {

        return getClientHttp("127.0.0.1", 9200);
    }

    public ElasticsearchClient getSimpleEsClientSSL(BufferedReader br) throws IOException, NoSuchAlgorithmException, KeyManagementException {

        System.out.println("Input username: ");
        String user = br.readLine();
        System.out.println("Input password:");
        String password = br.readLine();

        return getClientHttps("127.0.0.1", 9200, user, password);
    }

    public ElasticsearchClient getElasticSearchClient(BufferedReader br, ApiAccount apiAccount, Jedis jedis,byte[] symKey) throws Exception {

        boolean isSSL = true;

        //Check ES username
        String username = apiAccount.getUserName();
        if (username == null) {
            System.out.println("Create ES client without SSL? 'y' to confirm:");
            String input = br.readLine();
            if ("y".equals(input)) {
                isSSL = false;
            } else {
                System.out.println("Input ES username: ");
                input = br.readLine();
                if (!"".equals(input)) apiAccount.setUserName(input);
            }
        } else {
            System.out.println("ES username is: " + username + ". " +
                    "\nEnter to get client with it. " +
                    "\n'q' to quit. " +
                    "\n'r' to reset the user. " +
                    "\n'd' to delete it and get client without SSL:");
            String input = br.readLine();
            if ("q".equals(input)) return null;
            if ("r".equals(input)) {
                input = br.readLine();
                if (!"".equals(input)) apiAccount.setUserName(input);
            } else if ("d".equals(input)) {
                apiAccount.setUserName(null);
                isSSL = false;
            }
        }

        //Check ES password
        String password = null;
        if (isSSL) {
            password = getEsPassword(apiAccount,symKey);

            if (password == null) {
                System.out.println("Input the password of " + apiAccount.getUserName()+ " 'n' to create without SSL:");
                password = br.readLine();
                if ("n".equals(password)) {
                    apiAccount.setUserName(null);
                    isSSL = false;
                }
            }
        }

        //Create client
        IpAndPort ipAndPort = getIpAndPort(apiAccount.getApiUrl());
        if (ipAndPort == null) return null;
        try {
            if (isSSL) {
                esClient = getClientHttps(ipAndPort.ip(), ipAndPort.port(), apiAccount.getUserName(), password);
            } else esClient = getClientHttp(ipAndPort.ip(), ipAndPort.port());

            //Save encrypted password if there is a jedis
            if (esClient != null) {
                if (isSSL && password != null && jedis != null) {
                    setEncryptedEsPassword(password, apiAccount,symKey);
                }
            } else {
                log.debug("Create SSL ES client failed. Check ES and Config.json.");
                return null;
            }
        } catch (Exception e) {
            log.debug("Create SSL ES client failed. ");
            e.printStackTrace();
            return null;
        }

        return esClient;
    }

    @Nullable
    private static IpAndPort getIpAndPort(String apiUrl) {
        int doubleSlashIndex = apiUrl.indexOf("//");
        int colonIndex = apiUrl.lastIndexOf(":");
        String ip = apiUrl.substring(doubleSlashIndex+2,colonIndex);
        int port;
        try {
            port = Integer.parseInt(apiUrl.substring(colonIndex+1));
        }catch (Exception e){
            log.debug("Parsing ES port wrong.");
            return null;
        }
        return new IpAndPort(ip, port);
    }

    private record IpAndPort(String ip, int port) {}

    public String getEsPassword(ApiAccount apiAccount,byte[] symKey)  {
        String passwordCipher;
        try {
            passwordCipher = apiAccount.getPasswordCipher();
            if (passwordCipher == null) return null;
        } catch (Exception e) {
            return null;
        }
        String password;
        try {
            Decryptor decryptor =new Decryptor();
            byte[] passwordBytes = decryptor.decryptJsonBySymKey(passwordCipher,symKey).getData();
            if(passwordBytes==null)return null;
            password = new String(passwordBytes);
        } catch (Exception e) {
            log.debug("Decrypt ES password wrong.");
            return null;
        }
        return password;
    }

    public void setEncryptedEsPassword(String password, ApiAccount apiAccount, byte[] symKey)  {
        String esPasswordCipher = EccAes256K1P7.encryptWithSymKey(password.getBytes(), symKey);
        apiAccount.setPasswordCipher(esPasswordCipher);
        System.out.println("Your ES password is encrypted and saved locally.");
        log.debug("ES password is encrypted and saved locally.");
    }

    public ElasticsearchClient getEsClientSilent(ApiAccount apiAccount, byte[] symKey) {
        //Check ES username
        boolean isSSL = apiAccount.getUserName() != null;

        //Check ES password
        String password = null;
        if (isSSL) {
            password = getEsPassword(apiAccount,symKey);
            if (password == null) {
                isSSL = false;
            }
        }

        //Create client
        IpAndPort ipAndPort = getIpAndPort(apiAccount.getApiUrl());
        assert ipAndPort != null;
        try {
            if (isSSL) {
                esClient = getClientHttps(ipAndPort.ip, ipAndPort.port, apiAccount.getUserName(), password);
            } else {
                esClient = getClientHttp(ipAndPort.ip, ipAndPort.port);
            }

            if (esClient == null) {
                log.debug("Create SSL ES client failed. Check ES and Config.json.");
                return null;
            }
        } catch (Exception e) {
            log.debug("Create SSL ES client failed. ");
            e.printStackTrace();
            return null;
        }

        return esClient;
    }

    public ElasticsearchClient getClientHttp(String ip, int port) throws ElasticsearchException {

        System.out.println("Creating a client on " + ip + ":" + port + ".....");

        try {
            // Create a client without authentication check
            restClient = RestClient.builder(
                            new HttpHost(ip, port))
                    .setRequestConfigCallback(requestConfigBuilder -> {
                        return requestConfigBuilder.setConnectTimeout(5000 * 1000) // 连接超时（默认为1秒）
                                .setSocketTimeout(6000 * 1000);// 套接字超时（默认为30秒）//更改客户端的超时限制默认30秒现在改为100*1000分钟
                    })
                    .build();
            // Create the transport with a Jackson mapper
            transport = new RestClientTransport(
                    restClient, new JacksonJsonpMapper());
            // And create the API client
            esClient = new ElasticsearchClient(transport);
            try {
                String clusterName = esClient.info().clusterName();
                log.debug("Client has been created. Cluster name:" + clusterName);
            }catch (Exception e){
                System.out.println("Failed to connect ES server.");
                return null;
            }
            return esClient;

        } catch (Exception e) {
            e.printStackTrace();
            log.debug("The elasticsearch server may need a authorization. Try to create a client with HTTPS.");
            return null;
        }
    }


    public ElasticsearchClient getClientHttps(String host, int port, String username, String password) throws ElasticsearchException, IOException, NoSuchAlgorithmException, KeyManagementException {
        System.out.println("Creating a client with authentication on: " + host + ":" + port);

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        restClient = RestClient.builder(new HttpHost(host, port, "https"))
                .setHttpClientConfigCallback(h -> h.setDefaultCredentialsProvider(credentialsProvider))
                .setRequestConfigCallback(requestConfigBuilder -> {
                    return requestConfigBuilder.setConnectTimeout(5000 * 1000) // 连接超时（默认为1秒）
                            .setSocketTimeout(6000 * 1000);// 套接字超时（默认为30秒）//更改客户端的超时限制默认30秒现在改为100*1000分钟
                })
                .build();

        // Create the transport with a Jackson mapper
        transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        // And create the API client
        esClient = new ElasticsearchClient(transport);

        log.debug("Client has been created. Cluster name:" + esClient.info().clusterName());

        return esClient;
    }

    public void shutdownClient() throws IOException {
        if (this.esClient != null) this.esClient.shutdown();
        if (this.transport != null) this.transport.close();
        if (this.restClient != null) this.restClient.close();
    }

}
