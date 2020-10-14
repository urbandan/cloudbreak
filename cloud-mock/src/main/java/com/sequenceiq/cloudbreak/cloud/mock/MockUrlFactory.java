package com.sequenceiq.cloudbreak.cloud.mock;

import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.SslConfigurator;

import com.sequenceiq.cloudbreak.client.CertificateTrustManager;
import com.sequenceiq.cloudbreak.client.RestClientUtil;

public class MockUrlFactory {

    private MockUrlFactory() {

    }

    public static Invocation.Builder get(String path) throws KeyManagementException {
        CertificateTrustManager.SavingX509TrustManager x509TrustManager = new CertificateTrustManager.SavingX509TrustManager();
        TrustManager[] trustManagers = {x509TrustManager};
        SSLContext sslContext = SslConfigurator.newInstance().createSSLContext();
        sslContext.init(null, trustManagers, new SecureRandom());
        Client client = RestClientUtil.createClient(sslContext, false);
        WebTarget nginxTarget = client.target("https://localhost:10090");
        return nginxTarget.path(path).request();
    }
}
