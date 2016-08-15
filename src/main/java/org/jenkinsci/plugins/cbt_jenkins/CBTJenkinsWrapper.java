package org.jenkinsci.plugins.cbt_jenkins;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

//public class CBT_Jenkins extends Builder implements Serializable, SimpleBuildStep {

public class CBTJenkinsWrapper extends BuildWrapper implements Serializable {

	private static String username;
	private static String apikey;
	private static Screenshots screenshotBrowserLists;
	private static BrowserList seleniumBrowserList = new BrowserList();;

	/*
    private final String browserApiName;
    private final String operatingSystemApiName;
    private final String resolution;
    */
	
	private static String screenshotBrowserList;
	private static String screenshotUrl;
	    
    private List <JSONObject> seleniumTests;
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"

    
    @DataBoundConstructor
    public CBTJenkinsWrapper(String screenshotBrowserList, String screenshotUrl, List<JSONObject> seleniumTests) {
    	username = getDescriptor().getUsername();
    	apikey = getDescriptor().getApikey();
    	
    	this.screenshotBrowserList = screenshotBrowserList;
    	this.screenshotUrl = screenshotUrl;
    	this.seleniumTests = seleniumTests;
    }
    
    /*
    public List<JSONObject> searchReplace;
    @DataBoundConstructor
    public CBTJenkinsWrapper(List<JSONObject> searchReplace) {
    	System.out.println(searchReplace);
    	this.searchReplace = searchReplace;
    }
    */
    
    public String getScreenshotBrowserList() {
    	return this.screenshotBrowserList;
    }
    public String getScreenshotUrl() {
    	return this.screenshotUrl;
    }
    public List<JSONObject> getSeleniumTests() {
    	return this.seleniumTests;
    }

    /*
     *  Main function
     */
    @Override
    public Environment setUp(final AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	// This is where you 'build' the project.

    	// Do the screenshot tests
    	if (screenshotBrowserList != null && screenshotUrl != null && !screenshotUrl.equals("") && !screenshotBrowserList.equals("")) {
	    	HashMap<String, String> screenshotResults = screenshotBrowserLists.runScreenshotTest(screenshotBrowserList, screenshotUrl);
	    	if (!screenshotResults.isEmpty()) {
		    	listener.getLogger().println("\n-----------------------");
		    	listener.getLogger().println("SCREENSHOT TEST RESULTS");
		    	listener.getLogger().println("-----------------------");
		    	
		    	for (Map.Entry<String, String> screenshotResultsEntry : screenshotResults.entrySet()) {
		    		listener.getLogger().println(screenshotResultsEntry.getKey() + ": "+ screenshotResultsEntry.getValue());
		    	}
	    	}
    	}
    	// Do the selenium tests
    	if (!seleniumTests.isEmpty()) {
	    	listener.getLogger().println("\n---------------------");
	    	listener.getLogger().println("SELENIUM TEST RESULTS");
	    	listener.getLogger().println("---------------------");
	    	
	    	Iterator<JSONObject> i = seleniumTests.iterator();

	    	while(i.hasNext()) {
	    		JSONObject seTest = i.next();
    			String operatingSystemApiName = seTest.getString("operatingSystem");
    			String browserApiName = seTest.getString("browser");
    			String resolution = seTest.getString("resolution");
	
	    		FilePath workspace = build.getWorkspace();
		    	
		    	//really bad way to remove the build number from the name...
		    	String buildname = build.getFullDisplayName().substring(0, build.getFullDisplayName().length()-(String.valueOf(build.getNumber()).length()+1));
		
		    	// Set the environment variables
		    	EnvVars env = new EnvVars();
		    	env.put("CBT_USERNAME", username);
		    	env.put("CBT_APIKEY", apikey);
		    	env.put("CBT_BUILD_NAME", buildname);
		    	env.put("CBT_BUILD_NUMBER", String.valueOf(build.getNumber()));
		    	env.put("CBT_OPERATING_SYSTEM", operatingSystemApiName);
		    	env.put("CBT_BROWSER", browserApiName);
		    	env.put("CBT_RESOLUTION", resolution);
		    	
		    	// log the environment variables to the Jenkins build console
		    	listener.getLogger().println("\nEnvironment Variables");
		    	listener.getLogger().println("---------------------");
		    	for (Map.Entry<String, String> envvar : env.entrySet()) {
		    		listener.getLogger().println(envvar.getKey() + ": "+ envvar.getValue());
		    	}
		    	launcher = launcher.decorateByEnv(env); //add them to the tasklauncher
	
				for (FilePath executable : workspace.list()) {
			    	Launcher.ProcStarter lp = launcher.launch();
			    	lp.pwd(workspace); //set the working directory
					ArgumentListBuilder cmd = new ArgumentListBuilder();
					String fileName = executable.getName();
	
					//Extract extension
					String extension = "";
					int l = fileName.lastIndexOf('.');
					if (l > 0) {
					    extension = fileName.substring(l+1);
					}
					// figure out how to launch it					
					if (extension.equals("py") || extension.equals("rb") || extension.equals("jar") || extension.equals("js")) { //executes with full filename
						if (extension.equals("py")) { //python
							cmd.add("python");
						}else if (extension.equals("rb")) { //ruby
							cmd.add("ruby");
						}else if (extension.equals("jar")) { //java jar
							cmd.add("java");
							cmd.add("-jar");
						}else if (extension.equals("js")) { //node javascript
							cmd.add("node");
						}
						cmd.add(executable.getName());
					} else if (extension.equals("exe")) { //exe csharp
						FilePath csharpScriptPath = new FilePath(workspace, executable.getName()); 
						cmd.add(csharpScriptPath.toString());
					}
					
					lp.cmds(cmd);
					listener.getLogger().println("\nErrors/Output");
					listener.getLogger().println("-------------");
					//write the output from the script to the console
					lp.stdout(listener);
			    	lp.join(); //run the tests
				}
	    	}
    	}
		return new Environment(){};
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
        private String 	cbtUsername,
        				cbtApikey = "";
        
		public DescriptorImpl() throws IOException {
            load();
        }
		
    	public String getUsername() {
    		return cbtUsername;
    	}
    	public String getApikey() {
    		return cbtApikey;
    	}

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
   
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
/*
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }
*/
    	
        public ListBoxModel doFillOperatingSystemItems() {
        	ListBoxModel items = new ListBoxModel();
            for (int i=0 ; i<seleniumBrowserList.configurations.size() ; i++) {
            	Configuration config = seleniumBrowserList.configurations.get(i);
                items.add(config.getName(), config.getApiName());
            }          
            return items;
        }
        public ListBoxModel doFillBrowserItems(@QueryParameter String operatingSystem) {
            ListBoxModel items = new ListBoxModel();
            Configuration config = seleniumBrowserList.getConfig(operatingSystem);
            for (int i=0 ; i<config.browsers.size() ; i++) {
            	InfoPrototype configBrowser = config.browsers.get(i);
                items.add(configBrowser.getName(), configBrowser.getApiName());
        	}
            return items;
        }
        public ListBoxModel doFillResolutionItems(@QueryParameter String operatingSystem) {
            ListBoxModel items = new ListBoxModel();
            Configuration config = seleniumBrowserList.getConfig(operatingSystem);
            for (int i=0 ; i<config.resolutions.size() ; i++) {
            	InfoPrototype configResolution = config.resolutions.get(i);
                items.add(configResolution.getName());
        	}
            return items;
        }
        public ListBoxModel doFillScreenshotBrowserListItems() {
			screenshotBrowserLists = new Screenshots(cbtUsername, cbtApikey);
            ListBoxModel items = new ListBoxModel();

            for (int i=0 ; i<screenshotBrowserLists.browserLists.size() ; i++) {
            	String browserList = screenshotBrowserLists.browserLists.get(i);
                items.add(browserList);
            }
            return items;
        }
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "CrossBrowserTesting.com";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist configuration information,
            // set that to properties and call save().
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
        	cbtUsername = formData.getString("username");
        	cbtApikey = formData.getString("apikey");
            save();
            return super.configure(req,formData);            
        }
    }
}

