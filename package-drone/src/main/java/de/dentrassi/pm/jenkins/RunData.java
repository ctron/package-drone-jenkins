package de.dentrassi.pm.jenkins;

import java.io.Serializable;
import java.util.Date;

import hudson.model.Run;
import jenkins.model.Jenkins;

public class RunData implements Serializable {
    private static final long serialVersionUID = -6270671692552179049L;

    private String url;
    private Date time;
    private String id;
    private int number;
    private String fullName;

    RunData(Run<?, ?> run) {
        this.time = run.getTime();
        this.id = run.getId();
        this.number = run.getNumber();
        this.fullName = run.getParent().getFullName();
        final String jenkinsUrl = Jenkins.getInstance().getRootUrl();
        if (jenkinsUrl != null) {
            this.url = jenkinsUrl + run.getUrl();
        } else {
            this.url = run.getUrl();
        }
    }

    public String getUrl() {
        return url;
    }

    public Date getTime() {
        return time;
    }

    public String getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getFullName() {
        return fullName;
    }
}
