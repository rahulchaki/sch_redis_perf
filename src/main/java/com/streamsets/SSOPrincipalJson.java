package com.streamsets;


import java.util.*;

public class SSOPrincipalJson implements SSOPrincipal {
    private String tokenStr;
    private String issuerUrl;
    private long expires;
    private String principalId;
    private String principalName;
    private String organizationId;
    private String organizationName;
    private String email;
    private Set<String> roles = new HashSet();
    private Set<String> groups = new HashSet();
    private boolean app;
    private Map<String, String> attributes = new HashMap();
    private boolean locked;
    private static final ThreadLocal<String> REQUEST_IP_ADDRESS_TL = new ThreadLocal();

    public SSOPrincipalJson() {
    }

    public String getTokenStr() {
        return this.tokenStr;
    }

    public void setTokenStr(String tokenStr) {
        this.tokenStr = tokenStr;
    }

    public long getExpires() {
        return this.expires;
    }

    public void setExpires(long expires) {
        this.expires = expires;
    }

    public String getIssuerUrl() {
        return this.issuerUrl;
    }

    public void setIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    public String getName() {
        return this.getPrincipalId();
    }

    public String getPrincipalId() {
        return this.principalId;
    }

    public void setPrincipalId(String userId) {
        this.principalId = userId;
    }

    public String getPrincipalName() {
        return this.principalName;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

    public String getOrganizationId() {
        return this.organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganizationName() {
        return this.organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return this.roles;
    }

    public Set<String> getGroups() {
        return this.groups;
    }

    public boolean isApp() {
        return this.app;
    }

    public void setApp(boolean app) {
        this.app = app;
    }

    static void resetRequestIpAddress() {
        REQUEST_IP_ADDRESS_TL.remove();
    }

    void setRequestIpAddress(String ipAddress) {
        REQUEST_IP_ADDRESS_TL.set(ipAddress);
    }

    public String getRequestIpAddress() {
        return (String)REQUEST_IP_ADDRESS_TL.get();
    }

    public Map<String, String> getAttributes() {
        return this.attributes;
    }

    public void setRoles( Collection<String > roles ){
        this.roles.addAll( roles);
    }

    public void setGroups( Collection<String > groups ){
        this.groups.addAll( groups);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof SSOPrincipalJson) {
            SSOPrincipalJson that = (SSOPrincipalJson)o;
            return this.getName().equals(that.getName());
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.getName() == null ? 0 : this.getName().hashCode();
    }
}
