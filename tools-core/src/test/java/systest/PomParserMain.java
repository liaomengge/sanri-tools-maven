package systest;

import com.sanri.tools.modules.core.utils.MybatisXNode;
import com.sanri.tools.modules.core.utils.MybatisXPathParser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PomParserMain {

    @Test
    public void testPom() throws IOException, URISyntaxException {
        File baseDir = new File("d:/test/repository");baseDir.mkdir();
        String repository = "https://mirrors.huaweicloud.com/repository/maven/";

        ClassPathResource classPathResource = new ClassPathResource("classloaderdefault.pom");
        MybatisXPathParser mybatisXPathParser = new MybatisXPathParser(classPathResource.getInputStream());
        MybatisXNode mybatisXNode = mybatisXPathParser.evalNode("/project/dependencies");
        List<MybatisXNode> children = mybatisXNode.getChildren();
        if (CollectionUtils.isNotEmpty(children)) {
            for (MybatisXNode child : children) {
                String groupId = child.evalString("groupId");
                String artifactId = child.evalString("artifactId");
                String version = child.evalString("version");

                groupId = StringUtils.replace(groupId,".","/");

                Path resolve = Paths.get(baseDir.toURI()).resolve(Paths.get(groupId, artifactId, version));
                File targetDir = resolve.toFile();
                targetDir.mkdirs();

                String fileName = artifactId+"-"+version+".jar";
                URI resource = new URL(repository).toURI().resolve(new URI(groupId + "/" + artifactId + "/" + version+"/"+fileName));
                UrlResource urlResource = new UrlResource(resource);
                InputStream inputStream = urlResource.getInputStream();
                FileOutputStream fileOutputStream = new FileOutputStream(new File(targetDir, fileName));
                IOUtils.copy(inputStream,fileOutputStream);
                inputStream.close();
                fileOutputStream.close();
            }
        }
    }
}