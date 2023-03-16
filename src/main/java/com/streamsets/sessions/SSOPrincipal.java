package com.streamsets.sessions;


import java.security.Principal;
import java.util.Map;
import java.util.Set;

public interface SSOPrincipal extends Principal {
    String getTokenStr();

    String getIssuerUrl();

    long getExpires();

    String getPrincipalId();

    String getPrincipalName();

    String getOrganizationId();

    String getOrganizationName();

    String getEmail();

    Set<String> getRoles();

    Set<String> getGroups();

    Map<String, String> getAttributes();

    boolean isApp();

    String getRequestIpAddress();
}

