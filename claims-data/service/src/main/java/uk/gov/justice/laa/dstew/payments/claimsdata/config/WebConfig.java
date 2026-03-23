package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(1)
                .detectSupportedVersions(false)
                .addSupportedVersions("1","2")
                .setDefaultVersion("1")
                .setVersionRequired(false)
                .setVersionParser(new ApiVersionParser());
    }

}