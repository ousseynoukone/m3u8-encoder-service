package com.xksgroup.m3u8encoderv2.config;

import com.xksgroup.m3u8encoderv2.model.RequestIssuer;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

@Configuration
public class MongoConverters {

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(List.of(new ObjectToRequestIssuerConverter()));
    }

    @ReadingConverter
    static class ObjectToRequestIssuerConverter implements Converter<Object, RequestIssuer> {
        @Override
        public RequestIssuer convert(Object source) {
            if (source == null) return null;

            if (source instanceof String s) {
                return RequestIssuer.builder()
                        .issuerId(s)
                        .name(null)
                        .email(null)
                        .scope(null)
                        .build();
            }

            if (source instanceof Document doc) {
                return RequestIssuer.builder()
                        .issuerId(doc.getString("issuerId"))
                        .name(doc.getString("name"))
                        .email(doc.getString("email"))
                        .scope(doc.getString("scope"))
                        .build();
            }

            return null;
        }
    }
}
