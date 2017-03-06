package org.jenkinsci.plugins.cbt_jenkins;

import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.crossbrowsertesting.api.Account;
import com.crossbrowsertesting.api.ApiFactory;
import com.crossbrowsertesting.api.Screenshots;
import com.crossbrowsertesting.api.Selenium;
import com.crossbrowsertesting.configurations.Browser;
import com.crossbrowsertesting.configurations.OperatingSystem;
import com.crossbrowsertesting.configurations.Resolution;
import com.crossbrowsertesting.plugin.Constants;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

@Extension // This indicates to Jenkins that this is an implementation of an extension point.
public final class CBTDescriptor extends BuildWrapperDescriptor {
    private String 	globalUsername,
    				globalAuthkey,
    				buildUsername,
    				buildAuthkey = "";
	Screenshots screenshotApi;
	Selenium seleniumApi = new Selenium();
	Account account;
    
	public CBTDescriptor() throws IOException {
		super(CBTBuildWrapper.class);
        load();
    }
	
	public String getUsername() {
    	if (buildUsername != null && buildAuthkey != null && !buildUsername.isEmpty() && !buildAuthkey.isEmpty()) {
    		return buildUsername;
    	} else {
    		return globalUsername;
    	}
	}
	public String getAuthkey() {
    	if (buildUsername != null && buildAuthkey != null && !buildUsername.isEmpty() && !buildAuthkey.isEmpty()) {
			return buildAuthkey;
		} else {
			return globalAuthkey;
		}
	}
	public void setBuildCredentials(String username, String authkey) {
		buildUsername = username;
		buildAuthkey = authkey;
	}
	
    public String getDisplayName() {
        /*
         * This human readable name is used in the configuration screen.
         */
        return Constants.DISPLAYNAME;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
    	/*
         To persist configuration information,
         set that to properties and call save().
         Can also use req.bindJSON(this, formData);
         easier when there are many fields; need set* methods for this
         */
    	globalUsername = formData.getString("username");
    	globalAuthkey = formData.getString("authkey");
        save();
        return super.configure(req,formData);            
    }

	@Override
	public boolean isApplicable(AbstractProject<?, ?> item) {
		return true;
	}
	
	public String getVersion() {
		/*
		 * Get the version of plugin
		 */
		String fullVersion = getPlugin().getVersion();
		String stuffToIgnore = fullVersion.split("^\\d+[\\.]?\\d*")[1];
		return fullVersion.substring(0, fullVersion.indexOf(stuffToIgnore));
	}
	public void checkProxySettingsAndReloadRequest(ApiFactory af) {
    	// gets the proxy settings and reloads the Api Requests with them
    	Jenkins jenkins = Jenkins.getInstance();
    	try {
    		String hostname = jenkins.proxy.name;
    		int port = jenkins.proxy.port; // why is this throwing a null pointer if not set???
    		try { // we'll do these too, just in case it throws a NPE too
    			String proxyUsername = jenkins.proxy.getUserName();
    			String proxyPassword = jenkins.proxy.getPassword();
    			if (proxyUsername != null && proxyPassword != null && !proxyUsername.isEmpty() && !proxyPassword.isEmpty()) {
    				af.getRequest().setProxyCredentials(proxyUsername, proxyPassword);
    			}
    		} catch(NullPointerException npe) {
    			System.out.println("no proxy credentials were set");
    		} // no proxy credentials were set
        	af.getRequest().setProxy(hostname, port);
        	af.init();
    	} catch(NullPointerException npe) {
    		System.out.println("dont need to use a proxy");
    	} // dont need to use a proxy	
	}
    public ListBoxModel doFillOperating_systemItems() {
    	checkProxySettingsAndReloadRequest(seleniumApi);
    	
    	ListBoxModel items = new ListBoxModel();
    	try {
	        for (int i=0 ; i<seleniumApi.operatingSystems.size() ; i++) {
	        	OperatingSystem config = seleniumApi.operatingSystems.get(i);
	            items.add(config.getName(), config.getApiName());
	        }
        } catch(NullPointerException npe) {}
        return items;
    }
    public ListBoxModel doFillBrowserItems(@QueryParameter String operating_system) {
        ListBoxModel items = new ListBoxModel();
        try {
	        OperatingSystem config = seleniumApi.operatingSystems2.get(operating_system);
	        for (int i=0 ; i<config.browsers.size() ; i++) {
	        	Browser configBrowser = config.browsers.get(i);
	            items.add(configBrowser.getName(), configBrowser.getApiName());
	    	}
    	} catch(NullPointerException npe) {}

        return items;
    }
    public ListBoxModel doFillResolutionItems(@QueryParameter String operating_system) {
        ListBoxModel items = new ListBoxModel();
        try {
	        OperatingSystem config = seleniumApi.operatingSystems2.get(operating_system);
	        for (int i=0 ; i<config.resolutions.size() ; i++) {
	        	Resolution configResolution = config.resolutions.get(i);
	            items.add(configResolution.getName());
	    	}
        } catch(NullPointerException npe) {}
        return items;
    }
    public ListBoxModel doFillBrowserListItems() {
    	screenshotApi = new Screenshots(getUsername(), getAuthkey());
    	checkProxySettingsAndReloadRequest(screenshotApi);
		ListBoxModel items = new ListBoxModel();
		try {
	        for (int i=0 ; i<screenshotApi.browserLists.size() ; i++) {
	        	String browserList = screenshotApi.browserLists.get(i);
	            items.add(browserList);
	        }
    	} catch(NullPointerException npe) {}
        return items;
    }
    public ListBoxModel doFillCredentialsIdItems(final @AncestorInPath ItemGroup<?> context) {
        /*return new StandardUsernameListBoxModel()
                .withMatching(CredentialsMatchers.anyOf(
                        CredentialsMatchers.instanceOf(CBTCredentials.class)),
                        CredentialsProvider.lookupCredentials(
                                CBTCredentials.class,
                                context,
                                ACL.SYSTEM,
                                new ArrayList<DomainRequirement>())); */
    	return new StandardUsernameListBoxModel().withAll(CBTCredentials.all(context));
    }
    public FormValidation doTestConnection(@QueryParameter("username") final String username, @QueryParameter("authkey") final String authkey) throws IOException, ServletException {
    	account = new Account(username, authkey);
    	account.init();
    	if (account.connectionSuccessful) {
            return FormValidation.ok(Constants.AUTH_SUCCESS);
        } else {
            return FormValidation.error(Constants.AUTH_FAIL);
        }
    }
}
