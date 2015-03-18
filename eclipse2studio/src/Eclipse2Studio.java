import java.io.*;
import java.util.*;
import java.util.Properties;
import java.util.regex.*;

public class Eclipse2Studio
{
    private final static String TAB = "    ";
    private final static PrintStream cout = System.out;
    private final static Scanner cin = new Scanner(System.in);
    
    public static void main(String args[]){
        int argc = args.length;
        Eclipse2Studio convertor = new Eclipse2Studio();
        if(argc <= 0){
          String path = null;
          do{
              cout.println("Enter your original android project(eclipse) path. or 'exit' for exit.");
              path = cin.nextLine();
              convertor.convertFromEclipseToStudio(path);
              cout.println("\n");
          }while(!"exit".equalsIgnoreCase(path));
        } else{
          for(String path : args){
              Trace("Work on the path of "+path);
              convertor.convertFromEclipseToStudio(path);
          }
        }
    }
    
    /*********************Mainly worker modules***************/
    
    public boolean convertFromEclipseToStudio(String eclipseDir){
        File souDir = new File(eclipseDir);
        Trace("Convert Job begin!");
        if(!souDir.isDirectory()){
            exit("eclipse project folder not exits: "+souDir.getPath());
            return false;
        }
        
        File tarDir = new File(souDir.getParent(), souDir.getName()+"_astudio");
        
        try{
            Trace("Start copying source files from eclipse folder to studio folder.");
            clearDir(tarDir);
            copyEclipseFilesIntoStudioDir(eclipseDir, tarDir.getPath());
            Trace("Copying source files successfully.");
        }catch(IOException e){
            e.printStackTrace();
            exit("Convert job failed during step 1.\n");
            return false;
        }
        
        try{
            NVTreeBuilder builder = new NVTreeBuilder();
            GradleInfo info = GradleInfo.loadFromEclipseProj(eclipseDir);
            log("gradleInfo: "+info.toString());
            Trace("Obtain infos from eclipse project successfully.");
            builder.applyGradleInfos(info);
            NVTreeNode root = builder.build();
            List<String> output = new ArrayList<String>();
            printNVTree(output, root, 0);
            log("gradle script generated in memory: ");
            for(String s: output){
                log(s);
            }
            File gradleFile = new File(tarDir, "build.gradle");
            gradleFile.createNewFile();
            PrintStream fout = new PrintStream(gradleFile);
            for(String line: output){
                fout.println(line);
            }
            fout.close();
            Trace("Write to gradle script successfully.");
        }catch(Exception e){
            e.printStackTrace();
            exit("Convert job failed during step 2.\n");
            return false;
        }
        Trace("Convert Job done!");
        return true;
    }
    
    private void copyEclipseFilesIntoStudioDir(String eclipseDir, String studioDir) throws IOException{
        String sep = File.separator;
        String studioMainPath = studioDir+sep+"src"+sep+"main";
        log("copy eclipse-libs");
        copyDir(eclipseDir+sep+"libs", studioDir+sep+"libs");
        log("copy eclipse-src");
        copyDir(eclipseDir+sep+"src", studioMainPath+sep+"java");
        log("copy eclipse-res");
        copyDir(eclipseDir+sep+"res", studioMainPath+sep+"res");
        log("copy eclipse-assets");
        copyDir(eclipseDir+sep+"assets", studioMainPath+sep+"assets");
        log("copy manifest file");
        copyFile(eclipseDir+sep+"AndroidManifest.xml", studioMainPath+sep+"AndroidManifest.xml");
        log("copy the proguard config");
        copyFile(eclipseDir+sep+"proguard-project.txt", studioDir+sep+"proguard-rules.pro");
    }
    
    private void copyDir(String souDirPath, String tarDirPath) throws IOException{
        copyDir(new File(souDirPath), new File(tarDirPath));
    }
    
    private void copyDir(File souDir, File tarDir) throws IOException{
        //cout.println("copy from "+souDir+" to "+tarDir);
        if(!souDir.exists())
            return;
        if(!tarDir.exists()){
            tarDir.mkdirs();
        }
        for(File f : souDir.listFiles()){
            if(f.isFile()){
                copyFile(f, new File(tarDir, f.getName()));
            }else if(f.isDirectory()){
                copyDir(f, new File(tarDir, f.getName()));
            }
        }
    }
    
    private void copyFile(String souFile, String tarFile) throws IOException{
        copyFile(new File(souFile), new File(tarFile));
    }
    
    private void copyFile(File souFile, File tarFile) throws IOException{
        if(!souFile.exists())
            return;
        if(!tarFile.exists()){
            tarFile.createNewFile();
        }
        FileInputStream fin = new FileInputStream(souFile);
        FileOutputStream fout = new FileOutputStream(tarFile);
        byte[] buf = new byte[4096];
        int len = 0;
        while((len = fin.read(buf)) > 0){
            fout.write(buf, 0, len);
        }
        fin.close();
        fout.flush();
        fout.close();
    }
    
    private void clearDir(String dir) throws IOException{
        clearDir(new File(dir));
    }
    
    private void clearDir(File dir) throws IOException{
        if(!dir.isDirectory())
            return;
        for(File f : dir.listFiles()){
            if(f.isFile()){
                f.delete();
            }else if(f.isDirectory()){
                clearDir(f);
                f.delete();
            }
        }
    }
    
    private static String regexSearch(String text, Pattern pat){
        try{
            Matcher m = pat.matcher(text);
            if(m.find()){
                if(m.groupCount() > 0)
                    return m.group(1);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
    
    
    /*********************Gradle Project info collection***************/
    
    public static class GradleInfo{
        public final static Pattern PAT_TARGET = Pattern.compile("\\D*(\\d+).*");
        public final static Pattern PAT_REFERENCE = Pattern.compile("[\\/\\.\\\\]*([^\\/\\\\]+)$");
        public final static Pattern PAT_PACKAGE_NAME = Pattern.compile("package=\"(.*)\"");
        public final static Pattern PAT_MIN_SDK = Pattern.compile("minSdkVersion=\"(.*)\"");
        public final static Pattern PAT_TARGET_SDK = Pattern.compile("targetSdkVersion=\"(.*)\"");
        public final static Pattern PAT_VERSION_CODE = Pattern.compile("versionCode=\"(.*)\"");
        public final static Pattern PAT_VERSION_NAME = Pattern.compile("versionName=\"(.*)\"");
        private final static String androidSdkPath;
        private final static List<String> androidApiTargets;
        private final static List<String> androidBuildToolVers;
        public boolean isLibrary = false;
        public String compileSdkVersion = null; // necessary
        public String buildToolsVersion = null; // necessary
        public String applicationId = "";
        public String minSdkVersion = "8";
        public String targetSdkVersion = "";
        public String versionCode = "";
        public String versionName = "";
        public String minifyEnabled = "false";
        public String proguardFiles = "getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'";
        public List<String> dependencies;
        {
            dependencies = new ArrayList<String>();
        }
        private boolean includeLibRefs = false;
        
        static{
            androidSdkPath = getAndroidSDKPath();
            androidApiTargets = getAndroidApiTargets(androidSdkPath);
            androidBuildToolVers = getAndroidBuildToolVersions(androidSdkPath);
            log("Android sdk path: "+androidSdkPath);
            log("Android api targets: "+androidApiTargets);
            log("Android build tool verions: "+androidBuildToolVers);
        }
        
        public GradleInfo(){
            try{
                File userCfgFile = new File("user_config.prop");
                if(userCfgFile.isFile()) {
                    Properties userProp = new Properties();
                    userProp.load(new FileReader(userCfgFile));
                    buildToolsVersion = userProp.getProperty("buildToolsVersion");
                    compileSdkVersion = userProp.getProperty("compileApiVersion");
                    if("true".equalsIgnoreCase(userProp.getProperty("includeLibraryRefrence")))
                        includeLibRefs = true;
                }
            }catch(Exception e){
            }
            
            if(!androidBuildToolVers.isEmpty()){
                if(buildToolsVersion == null || !androidBuildToolVers.contains(androidBuildToolVers)){
                    buildToolsVersion = androidBuildToolVers.get(androidBuildToolVers.size()-1);
                }
            }
        }
        
        private static String getProperApiLevel(String hopeApiLevel){
            if(!androidApiTargets.isEmpty()){
                if(hopeApiLevel == null || !androidApiTargets.contains(hopeApiLevel)) {
                    hopeApiLevel = androidApiTargets.get(androidApiTargets.size()-1);
                }
            }
            return hopeApiLevel;
        }
        
        public static GradleInfo loadFromEclipseProj(String eclipseDir){
            GradleInfo info = new GradleInfo();
            
            Trace("Parse eclipse android project's properties file.");
            try{
                Properties projProp = new Properties();
                File propFile = new File(eclipseDir, "project.properties");
                if(!propFile.exists())
                    propFile = new File(eclipseDir, "default.properties");
                if(!propFile.exists()) {
                    exit("can not find file: project.properties.");
                    return null;
                }
                projProp.load(new FileReader(propFile));
              
                info.isLibrary = "true".equalsIgnoreCase((projProp.getProperty("android.library")+"").trim());
                String originalTarget = regexSearch(projProp.getProperty("target"), PAT_TARGET);
                info.compileSdkVersion = getProperApiLevel(originalTarget);
                for(int i=1; i<50; i++){
                    String ref = projProp.getProperty("android.library.reference."+i);
                    if(ref == null || ref.trim().length() <= 0)
                        break;
                    info.dependencies.add("project(':"+regexSearch(ref, PAT_REFERENCE)+"')");
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            
            Trace("Parse eclipse android project's manifest file.");
            try{
                File maniFile = new File(eclipseDir, "AndroidManifest.xml");
                if(!maniFile.exists()) {
                    exit("can not find file: AndroidManifest.xml.");
                    return null;
                }
                String wholeText = "";
                Scanner fin = new Scanner(maniFile);
                while(fin.hasNextLine()){
                    wholeText += fin.nextLine() + "\n";
                }
                fin.close();
                info.applicationId = regexSearch(wholeText, PAT_PACKAGE_NAME);
                info.minSdkVersion = regexSearch(wholeText, PAT_MIN_SDK);
                info.targetSdkVersion = regexSearch(wholeText, PAT_TARGET_SDK);
                info.versionCode = regexSearch(wholeText, PAT_VERSION_CODE);
                info.versionName = regexSearch(wholeText, PAT_VERSION_NAME);
            }catch(Exception e){
                e.printStackTrace();
            }
        
            return info;
        }
        
        @Override
        public String toString() {
            return "GradleInfo [isLibrary=" + isLibrary + ", compileSdkVersion="
                    + compileSdkVersion + ", buildToolsVersion="
                    + buildToolsVersion + ", applicationId="
                    + applicationId + ", minSdkVersion=" + minSdkVersion
                    + ", targetSdkVersion=" + targetSdkVersion
                    + ", versionCode=" + versionCode + ", versionName="
                    + versionName + ", minifyEnabled=" + minifyEnabled
                    + ", proguardFiles=" + proguardFiles
                    + ", dependencies=" + dependencies + "]";
        }
        
    }
    
    private static List<String> getAndroidApiTargets(String sdkPath){
        List<String> apis = new ArrayList<String>(); // change to integer after api-100
        File platforms = new File(sdkPath+"", "platforms");
        if(!platforms.isDirectory())
            return apis;
        File sourceProp = null;
        for(File apiFolder : platforms.listFiles()){
            sourceProp = new File(apiFolder, "source.properties");
            if(!sourceProp.isFile())
                continue;
            try{
                Properties prop = new Properties();
                prop.load(new FileReader(sourceProp)); //AndroidVersion.ApiLevel
                String apiLevel = prop.getProperty("AndroidVersion.ApiLevel");
                if(apiLevel != null && apiLevel.trim().length()>0)
                    apis.add(apiLevel.trim());
            }catch(Exception e){}
        }
        Collections.sort(apis);
        return apis;
    }
    
    private static List<String> getAndroidBuildToolVersions(String sdkPath){
        List<String> vers = new ArrayList<String>();
        File platforms = new File(sdkPath+"", "build-tools");
        if(!platforms.isDirectory())
            return vers;
        File sourceProp = null;
        for(File folder : platforms.listFiles()){
            sourceProp = new File(folder, "source.properties");
            if(!sourceProp.isFile())
                continue;
            try{
                Properties prop = new Properties();
                prop.load(new FileReader(sourceProp)); //AndroidVersion.ApiLevel
                String ver = prop.getProperty("Pkg.Revision");
                if(ver != null && ver.trim().length()>0)
                    vers.add(ver.trim());
            }catch(Exception e){}
        }
        Collections.sort(vers);
        return vers;
    }
    
    private static String getAndroidSDKPath(){
        Map<String,String> sysEnv = System.getenv();
        String envPath = null;
        for(Map.Entry<String,String> e : sysEnv.entrySet()){
            if("path".equalsIgnoreCase(e.getKey())) {
                envPath = e.getValue();
                break;
            }
        }
        if(envPath == null)
            return null;
        boolean isUnixPath = envPath.contains("/");
        String[] paths = envPath.split(isUnixPath ? ":" : ";");
        for(String s : paths){
            String sdkPath = searchAndroidSDKPath(new File(s));
            if(sdkPath != null && sdkPath.trim().length() > 0)
                return sdkPath;
        }
        return null;
    }
    
    private static String searchAndroidSDKPath(File dir){
        if(dir == null || !dir.isDirectory())
            return null;
        File[] dirs = new File[]{dir.getParentFile(), dir};
        String[] sdkFolders = new String[]{"platforms","build-tools","tools","platform-tools"};
        for(File d : dirs){
            if(d == null || !d.isDirectory())
                continue;
            boolean sdkDetected = true;
            for(String sdkFolder : sdkFolders){
                if(!new File(d, sdkFolder).isDirectory()){
                    sdkDetected = false;
                    break;
                }
            }
            if(sdkDetected)
                return d.getAbsolutePath();
        }
        return null;
    }
    
    
    
    /*********************Gradle File Tree Model**********************/
    
    public static class NVTreeNode{
        public String nodeName;
        public NVTreeNode father;
        public List<NVTreeNode> children;
        public List<NVPair> payload;
        
        public NVTreeNode(NVTreeNode fatherNode, String name){
            father = fatherNode;
            this.nodeName = name;
            children = new ArrayList<NVTreeNode>();
        }
        
        public boolean isEmpty(){
            return (children == null || children.isEmpty()) && (payload == null || payload.isEmpty());
        }
    }
    
    public static class NVPair{
        String name, value;
        public NVPair(String name, String value){
            this.name = name;
            this.value = value;
        }
    }
    
    private class NVTreeBuilder{
        public static final String ROOT_NODE = "__ROOT_NODE__";
        private Map<String, ArrayList<String>> branches;
        private Map<String, ArrayList<NVPair>> payloads;
        private Set<String> visits;
        
        /** father can be passed null which indicates the ROOT_NODE.
        */
        public NVTreeBuilder addBranch(String father, String child){
            if(branches == null)
                branches = new HashMap<String, ArrayList<String>>();
            if(father == null)
                father = ROOT_NODE;
            ArrayList<String> branch = branches.get(father);
            if(branch == null){
                branch = new ArrayList<String>();
                branches.put(father, branch);
            }
            if(!branch.contains(child))
                branch.add(child);
            return this;
        }
        
        /** nodeName can be passed null which indicates the ROOT_NODE.
        */
        public NVTreeBuilder addPayload(String nodeName, String payloadName, String payloadValue){
            if(payloads == null)
                payloads = new HashMap<String, ArrayList<NVPair>>();
            if(nodeName == null)
                nodeName = ROOT_NODE;
            ArrayList<NVPair> payload = payloads.get(nodeName);
            if(payload == null){
                payload = new ArrayList<NVPair>();
                payloads.put(nodeName, payload);
            }
            payload.add(new NVPair(payloadName, payloadValue));
            return this;
        }
        
        public void applyGradleInfos(GradleInfo info) {
            reset();
            boolean isLibrary = info.isLibrary;
            String plugin = isLibrary ? "'com.android.library'" : "'com.android.application'";
            String applicationId = isLibrary ? null : '"'+info.applicationId+'"';
            addBranch(null, "android")
            .addBranch(null, "dependencies")
            .addPayload(null, "apply plugin:", plugin);
               
            addBranch("android", "defaultConfig")
            .addBranch("android", "buildTypes")
            .addPayload("android", "compileSdkVersion", info.compileSdkVersion)
            .addPayload("android", "buildToolsVersion", '"'+info.buildToolsVersion+'"');
            
            addPayload("defaultConfig", "applicationId", applicationId)
                   .addPayload("defaultConfig", "minSdkVersion", info.minSdkVersion)
                   .addPayload("defaultConfig", "targetSdkVersion", info.targetSdkVersion)
                   .addPayload("defaultConfig", "versionCode", info.versionCode)
                   .addPayload("defaultConfig", "versionName", '"'+info.versionName+'"');
                   
            addBranch("buildTypes", "release")
                   .addPayload("release", "minifyEnabled", "false")
                   .addPayload("release", "proguardFiles", "getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'");
            
            addPayload("dependencies", "compile", "fileTree(dir: 'libs', include: ['*.jar'])");
            if(info.includeLibRefs) {
                for(String ref : info.dependencies){
                    addPayload("dependencies", "compile", ref);
                }
            }
        }
        
        public NVTreeNode build(){
            visits = new HashSet<String>();
            NVTreeNode root = new NVTreeNode(null, null);
            buildTree(root);
            Trace("NVTree Build Successfully.");
            return root;
        }
        
        private void buildTree(NVTreeNode root){
            String nodeName = root.nodeName == null ? ROOT_NODE : root.nodeName;
            if(visits.contains(nodeName))
                return;
            visits.add(nodeName);
            root.payload = payloads.get(nodeName);
            
            List<String> branch = branches.get(nodeName);
            //cout.println(nodeName+", brn="+(branch == null ? "null" : branch.size()+""));
            if(branch != null && !branch.isEmpty()){
                root.children = new ArrayList<NVTreeNode>();
                for(String childName : branch){
                    NVTreeNode childNode = new NVTreeNode(root, childName);
                    root.children.add(childNode);
                    buildTree(childNode);
                }
            }
        }
        
        public void reset(){
            if(branches != null)
                branches.clear();
            if(payloads != null)
                payloads.clear();
        }        
    }
    
    public void printNVTree(List<String> output, NVTreeNode root, int depth){
        if(root.isEmpty())
            return;
    
        String tabs = "";
        for(int i=0; i<depth; i++)
            tabs += TAB;
        
        boolean hasPayload = false;
        if(root.payload != null){
            for(NVPair pair : root.payload){
                if(pair.value == null || pair.value.length() <= 0)
                    continue;
                output.add(tabs+pair.name+" "+pair.value);
                hasPayload = true;
            }
        }
        
        if(root.children != null && !root.children.isEmpty()) {
            if(hasPayload)
                output.add("");
            for(NVTreeNode node : root.children){
                output.add(tabs+node.nodeName+" {");
                printNVTree(output, node, depth+1);
                output.add(tabs+"}");
                if(depth == 0)
                    output.add("");
            }
        }
    }
    
    private static void exit(){
        cout.println("Error occur. Break running.");
    }
    
    private static void exit(String dyingWords){
        cout.println(dyingWords);
    }
    
    private static void Trace(String TraceMsg){
        cout.println("[##] "+TraceMsg+"");
    }
    
    private static void log(String logMsg){
        cout.println(logMsg+"");
    }
    
}