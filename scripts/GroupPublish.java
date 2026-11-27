import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Publishes only the prepared personal branch after the user signs into GitHub. Java 17+. */
public class GroupPublish {
 static String git="git";
 static int run(String... args)throws Exception{
  List<String> command=new ArrayList<>();command.add(git);command.addAll(Arrays.asList(args));
  return new ProcessBuilder(command).inheritIO().start().waitFor();
 }
 static String read(String... args)throws Exception{
  List<String> command=new ArrayList<>();command.add(git);command.addAll(Arrays.asList(args));
  Process p=new ProcessBuilder(command).redirectErrorStream(true).start();String value=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
  if(p.waitFor()!=0)throw new IOException("Git command failed: "+value);return value;
 }
 public static void main(String[] args){
  try{
   Path file=Path.of(".local/group-runtime.properties");
   if(Files.isRegularFile(file)){Properties cfg=new Properties();try(InputStream in=Files.newInputStream(file)){cfg.load(in);}git=cfg.getProperty("gitExecutable","git");}
   String branch=read("branch","--show-current"),remote=read("remote","get-url","origin");
   if(!"m5-m6-administration".equals(branch)||!"https://github.com/wycAWAKE/883project.git".equals(remote))throw new IOException("This helper only publishes the prepared M5/M6 branch to the agreed group repository.");
   if(run("diff","--quiet")!=0||!read("ls-files","--others","--exclude-standard").isEmpty())throw new IOException("The working copy has unstaged changes. Review and test those changes before publishing.");
   if(Arrays.asList(args).contains("--check")){System.out.println("Branch and destination verified. No commit or network write performed.");return;}
   if(run("diff","--cached","--quiet")!=0){
    run("diff","--cached","--stat");System.out.print("Your GitHub username (for a private noreply commit address): ");
    String username=new BufferedReader(new InputStreamReader(System.in)).readLine();
    if(username==null||!username.matches("[A-Za-z0-9-]{1,39}"))throw new IOException("Enter a valid GitHub username. No password or token is needed here.");
    if(run("-c","user.name=Jianning Tan","-c","user.email="+username+"@users.noreply.github.com","commit","-m","Integrate document applications and progress notifications into SmartCampus")!=0)throw new IOException("Commit failed. Read the Git output.");
   }
   System.out.println("Publishing the personal branch. Complete GitHub sign-in if prompted. Your account needs write access.");
   if(run("push","-u","origin","m5-m6-administration")!=0)throw new IOException("Push failed. Check GitHub login and the repository invitation. Local files and commits are retained.");
   System.out.println("Pushed successfully. Group review: https://github.com/wycAWAKE/883project/compare/main...m5-m6-administration");
  }catch(Exception e){System.err.println(e.getMessage());System.exit(1);}
 }
}
