import org.apache.tools.ant.util.FileUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.nio.file.CopyOption
import java.nio.file.Files
import java.text.ParseException

/**
 * Created by esaskum on 5/15/14.
 */
class Wallie_iLift
{
    def PROXY_HOST = 'www-proxy.ericsson.se'
    def PROXY_PORT = '8080'
    def TEST_URL = "http://bing.com"
    def HOST_URL = 'http://interfacelift.com/'
    def PAGE_URL = 'wallpaper/downloads/date/any/'
    def SIZE = '1366x768' //'1280x1024' //
    def IMAGE_NO = '1'
    String DOWNLOAD_FOLDER = System.getProperty("user.home") + File.separator + "wallie_iLift-wallpapers"
    String NITROGEN_CONFIG = System.getProperty("user.home") + "/.config/nitrogen/bg-saved.cfg"
    int MAX_DOWNLOADS = 10
    String ENV_PREFIX = "WALLIE_"

    Wallie_iLift()
    {
        def env = System.getenv()
        Wallie_iLift ins = this;

        env.each {
            if(it.key.startsWith(ENV_PREFIX))
            {
                def actualKey = it.key.replace(ENV_PREFIX, "")
                def value = it.value
                Wallie_iLift.metaClass.properties.each { var ->
//                    println('Matching ' + actualKey + ':' + var.name)
                    if(var.name == actualKey)
                    {
                        var.setProperty(ins, value)
                        println('Configuration property overriden, ' + var.name + '=' + ins.getProperty(var.name))
                    }
                }
            }
        }

    }

    void start()
    {
        checkInternetConnection()

        println "Connecting to host"
        Document doc = fetchDownloadPage()

        println "Extracting download link"
        String link  = extractDownloadLink(doc)

        println "Link:" + link

        File file = downloadWallpaper(link)
        if(setWallpaper(file) != null)
            println "Wallpaper is set to: " + file

        cleanupDownloadFolder()
    }

    void checkInternetConnection()
    {
        /** def response = Jsoup.connect(TEST_URL).execute()
        if(response != 200)
        {
            println "Unable to connect to internet, setting proxy and trying ..."
            System.properties.putAll( ["http.proxyHost":PROXY_HOST, "http.proxyPort":PROXY_PORT] )

            response = Jsoup.connect(TEST_URL).execute()
            if(response.statusCode() != 200)
            {
                println "Unable to connect to internet!"
                System.exit(0);
            }
        }
        println "Connection OK" **/

System.properties.putAll( ["http.proxyHost":PROXY_HOST, "http.proxyPort":PROXY_PORT] )
            System.properties.putAll( ["https.proxyHost":PROXY_HOST, "https.proxyPort":PROXY_PORT] )
            
        if(testURLConnection() == -200) 
        {
            System.properties.putAll( ["http.proxyHost":PROXY_HOST, "http.proxyPort":PROXY_PORT] )
            System.properties.putAll( ["https.proxyHost":PROXY_HOST, "https.proxyPort":PROXY_PORT] )
            if(testURLConnection() == -200) 
            {
                println "Unable to connect to internet!"
                System.exit(0);
            }
        }
    }

    int testURLConnection() 
    {
        def responseCode = -200;
        try 
        {
            def response = Jsoup.connect(TEST_URL).execute()
            responseCode = response.statusCode()
        }
        catch(Exception e)
        {
            println "Error connecting to URL " + TEST_URL
            println e.message
        }

        (responseCode == 200) ? 200 : -200;
    }

    Document fetchDownloadPage()
    {
        return Jsoup.connect(HOST_URL + PAGE_URL)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.137 Safari/537.36")
                .get();
    }

    String extractDownloadLink(Document document)
    {
        def elements = document.getElementsByTag("select")
        if(elements.isEmpty())
        {
            String err = 'No <select> elements could be parsed!'
            println(err + document.html())
            throw new ParseException(err, 0)
        }

        def element = elements[IMAGE_NO.toInteger()-1]
        def onchange = element.attr("onchange")
        onchange = onchange.replace("javascript:imgload('", "").replace(")", "").replace("'", "")
        def args = onchange.split(",")

        def arg_base = args[0]
        def arg_val = SIZE
        def arg_padded = args[2]

        while(arg_padded.length() < 5) arg_padded = 0 + arg_padded;

        return HOST_URL + 'wallpaper/7yz4ma1/' + arg_padded + '_' + arg_base + '_' + arg_val + '.jpg'
    }

    def downloadWallpaper(String downloadUrl)
    {
        def response = Jsoup.connect(downloadUrl)
                .followRedirects(true)
                .ignoreContentType(true)
                .execute()

        if(!response.contentType().equals("image/jpeg"))
        {
            throw new Exception("Unable to download picture from: " + downloadUrl)
        }

        def fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"), downloadUrl.length())
        fileName = fileName.replace("/", "")

//        def splitIndex = fileName.lastIndexOf(".")
//        def size = fileName.size()
//        String[] fileNameParts = [ fileName.substring(0, splitIndex) ,
//                                   fileName.substring(splitIndex+1, size) ]

        File f = new File(fileName)
        f.deleteOnExit()
        f << response.bodyAsBytes()

        return f
    }

    def setWallpaper(File srcFile)
    {
        File folder = new File(DOWNLOAD_FOLDER)
        if(!folder.exists())
        {
            folder.mkdirs()
        }

        File destFile = new File(DOWNLOAD_FOLDER + File.separator + srcFile.getName())
        if(destFile.exists())
        {
            println("File " + srcFile.getName() + " already exists")
            println("Wallpaper not changed.")
            return null
        }

        Files.move(srcFile.toPath(), destFile.toPath())

        def command = "gsettings set org.gnome.desktop.background picture-uri file://" +
                destFile.absolutePath
        command.execute()
/**
        def nitrogenConfigFile = new File(NITROGEN_CONFIG)
        def buffer = "";

        nitrogenConfigFile.eachLine {
            if(it.startsWith("file")) {
                it = "file=" + destFile.absolutePath
            }
            buffer = it + '\n'
        }

        nitrogenConfigFile << buffer

        "nitrogen --restore".execute()
**/
        return destFile
    }

    def cleanupDownloadFolder()
    {
        File downloadFolder =  new File(DOWNLOAD_FOLDER)
        def files = downloadFolder.listFiles().sort() {
             a,b -> a.lastModified().compareTo b.lastModified()
        }

        if(files.length > MAX_DOWNLOADS)
        {            
            files[0].delete()
            println "Files in download folder trimmed to " + MAX_DOWNLOADS + "files"
        }
        else
        {
            println "No files to cleanup"
        }
    }

    public static void main(String[] args) {
        new Wallie_iLift().start()
    }
}
