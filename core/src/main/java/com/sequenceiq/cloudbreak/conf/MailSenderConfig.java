package com.sequenceiq.cloudbreak.conf;

import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_MAIL_SMTP_AUTH;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_MAIL_SMTP_STARTTLS_ENABLE;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_SMTP_SENDER_FROM;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_SMTP_SENDER_HOST;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_SMTP_SENDER_PASSWORD;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_SMTP_SENDER_PORT;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_SMTP_SENDER_USERNAME;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;
import org.springframework.util.StringUtils;

import freemarker.template.TemplateException;

@Configuration
public class MailSenderConfig {
    @Value("${cb.smtp.sender.host:" + CB_SMTP_SENDER_HOST + "}")
    private String host;

    @Value("${cb.smtp.sender.port:" + CB_SMTP_SENDER_PORT + "}")
    private int port;

    @Value("${cb.smtp.sender.username:" + CB_SMTP_SENDER_USERNAME + "}")
    private String userName;

    @Value("${cb.smtp.sender.password:" + CB_SMTP_SENDER_PASSWORD + "}")
    private String password;

    @Value("${cb.smtp.sender.from:" + CB_SMTP_SENDER_FROM + "}")
    private String msgFrom;

    @Value("${cb.mail.smtp.auth:" + CB_MAIL_SMTP_AUTH + "}")
    private String smtpAuth;

    @Value("${cb.mail.smtp.starttls.enable:" + CB_MAIL_SMTP_STARTTLS_ENABLE + "}")
    private String smtpStarttlsEnable;

    @Bean
    public JavaMailSender mailSender() {
        JavaMailSender mailSender = null;
        if (isMailSendingConfigured()) {
            mailSender = new JavaMailSenderImpl();
            ((JavaMailSenderImpl) mailSender).setHost(host);
            ((JavaMailSenderImpl) mailSender).setPort(port);
            if (!StringUtils.isEmpty(userName)) {
                ((JavaMailSenderImpl) mailSender).setUsername(userName);
            }
            if (!StringUtils.isEmpty(password)) {
                ((JavaMailSenderImpl) mailSender).setPassword(password);
            }
            ((JavaMailSenderImpl) mailSender).setJavaMailProperties(getJavaMailProperties());
        } else {
            mailSender = new DummyEmailSender();
        }

        return mailSender;
    }

    private boolean isMailSendingConfigured() {
        // some SMTP servers don't need username/password
        return !StringUtils.isEmpty(host)
                && !StringUtils.isEmpty(msgFrom);
    }

    private String missingVars() {
        List<String> missingVars = new ArrayList();
        if (StringUtils.isEmpty(host)) {
            missingVars.add("cb.smtp.sender.host");
        }
        if (StringUtils.isEmpty(userName)) {
            missingVars.add("cb.smtp.sender.username");
        }
        if (StringUtils.isEmpty(password)) {
            missingVars.add("cb.smtp.sender.password");
        }
        if (StringUtils.isEmpty(msgFrom)) {
            missingVars.add("cb.smtp.sender.from");
        }
        return StringUtils.collectionToDelimitedString(missingVars, ",", "[", "]");
    }

    private Properties getJavaMailProperties() {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", smtpStarttlsEnable);
        props.put("mail.debug", true);
        return props;
    }

    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() throws IOException, TemplateException {
        FreeMarkerConfigurationFactoryBean factoryBean = new FreeMarkerConfigurationFactoryBean();
        factoryBean.setPreferFileSystemAccess(false);
        factoryBean.setTemplateLoaderPath("classpath:/");
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    private final class DummyEmailSender implements JavaMailSender {
        private final Logger logger = LoggerFactory.getLogger(DummyEmailSender.class);
        private final String msg = "SMTP not configured! Related configuration entries: " + missingVars();

        @Override
        public MimeMessage createMimeMessage() {
            return null;
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return null;
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            logger.info(msg);
        }

        @Override
        public void send(MimeMessage[] mimeMessages) throws MailException {
            logger.info(msg);
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            logger.info(msg);
        }

        @Override
        public void send(MimeMessagePreparator[] mimeMessagePreparators) throws MailException {
            logger.info(msg);
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            logger.info(msg);
        }

        @Override
        public void send(SimpleMailMessage[] simpleMessages) throws MailException {
            logger.info(msg);
        }
    }

}
