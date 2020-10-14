package com.sequenceiq.it.cloudbreak.mock.freeipa.healthcheck;

import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.SslConfigurator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.client.CertificateTrustManager;
import com.sequenceiq.cloudbreak.client.RestClientUtil;
import com.sequenceiq.freeipa.client.healthcheckmodel.CheckResult;
import com.sequenceiq.it.cloudbreak.exception.TestFailException;
import com.sequenceiq.it.cloudbreak.mock.ITResponse;

import spark.Request;
import spark.Response;

@Component
public class FreeIpaNodeHealthCheckHandler extends ITResponse {

    private CheckResult result = new CheckResult();

    private HttpStatus status = HttpStatus.OK;

    public FreeIpaNodeHealthCheckHandler() {
        setHealthy();
    }

    public void setHealthy() {
        setStatusOfFreeipa(HttpStatus.OK);
    }

    public void setUnreachable() {
        setStatusOfFreeipa(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void setStatusOfFreeipa(HttpStatus status) {
        CertificateTrustManager.SavingX509TrustManager x509TrustManager = new CertificateTrustManager.SavingX509TrustManager();
        TrustManager[] trustManagers = {x509TrustManager};
        SSLContext sslContext = SslConfigurator.newInstance().createSSLContext();
        try {
            sslContext.init(null, trustManagers, new SecureRandom());
        } catch (KeyManagementException e) {
            throw new TestFailException("Cannot init SSL Context: " + e.getMessage(), e);
        }
        Client client = RestClientUtil.createClient(sslContext, false);
        WebTarget target = client.target(String.format("https://%s:%d", "localhost", 10090));
        target = target.path("/ipa/status/configure").queryParam("status", status.name());
        try (javax.ws.rs.core.Response ignore = target.request().get()) {
        }
    }

    @Override
    public Object handle(Request request, Response response) {
        response.status(status.value());
        if (result == null) {
            return "";
        }
        return result;
    }
}
