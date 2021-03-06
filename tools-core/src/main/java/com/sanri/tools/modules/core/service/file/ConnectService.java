package com.sanri.tools.modules.core.service.file;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanri.tools.modules.core.dtos.ConnectDto;
import com.sanri.tools.modules.core.dtos.param.*;
import com.sanri.tools.modules.core.utils.NetUtil;
import com.sanri.tools.modules.core.dtos.ConfigPath;
import com.sanri.tools.modules.core.exception.ToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConnectService {
    @Autowired
    private FileManager fileManager;
    // 连接都保存在这个目录
    public static final String MODULE = "connect";

    /**
     * 测试连接是否是通的
     * @param connectParam
     */
    public void testConnectReachable(ConnectParam connectParam){
        String host = connectParam.getHost();
        int port = connectParam.getPort();
        boolean hostConnectable = NetUtil.isHostConnectable(host, port);
        if (!hostConnectable){
            throw new ToolException("连接失败 "+host+":"+port);
        }
    }

    /**
     * 模块列表
     * @return
     */
    public List<String> modules(){
        List<ConfigPath> configPaths = fileManager.configNames(MODULE);
        return configPaths.stream().map(ConfigPath::getPathName).collect(Collectors.toList());
    }

    /**
     * 指定模块下的连接列表
     * @param module
     * @return
     */
    public Set<String> names(String module){
        return fileManager.simpleConfigNames(MODULE,module);
    }

    /**
     * 获取连接详情
     * @param module
     * @param connName
     * @return
     */
    public String content( String module,String connName) throws IOException {
        return fileManager.readConfig(MODULE,module+"/"+connName);
    }

    /**
     * 创建连接
     * @param databaseConnectParam
     * @throws IOException
     */
    public void createConnect(String module,String data) throws IOException {
        Class<?> param = moduleParamFactory(module);
        if(param == null){
            throw new IllegalArgumentException("模块配置不受支持:"+module);
        }
        AbstractConnectParam abstractConnectParam = (AbstractConnectParam) JSON.parseObject(data, param);
        ConnectParam connectParam = abstractConnectParam.getConnectParam();
        if (connectParam != null) {
            NetUtil.isHostConnectable(connectParam.getHost(), connectParam.getPort());
        }

        String connName = abstractConnectParam.getConnectIdParam().getConnName();
        fileManager.writeConfig(MODULE,module+"/"+connName, data);
    }

    private Class<?> moduleParamFactory(String module) {
        switch (module){
            case "database":
                return DatabaseConnectParam.class;
            case "kafka":
                return KafkaConnectParam.class;
            case "redis":
                return RedisConnectParam.class;
            case "zookeeper":
                return ZookeeperConnectParam.class;
        }
        return null;
    }

    ObjectMapper objectMapper = new ObjectMapper();

    // 使用 fastjson 在反序列化 kafka 的时候有问题, 这里换成 jackson
    public AbstractConnectParam readConnParams(String module,String connName) throws IOException {
        String content = content(module, connName);
        Class<?> paramClass = moduleParamFactory(module);
//        AbstractConnectParam abstractConnectParam = (AbstractConnectParam) JSON.parseObject(content, paramClass);
        Object readValue = objectMapper.readValue(content, paramClass);
        return (AbstractConnectParam) readValue;
    }

    /**
     * 列出所有连接
     * @return
     */
    public List<ConnectDto> selectAll() {
        List<ConnectDto> connects = new ArrayList<>();
        List<ConfigPath> configPaths = fileManager.configNames(MODULE);
        for (ConfigPath configPath : configPaths) {
            String childModule = configPath.getPathName();
            List<ConfigPath> childs = fileManager.configChildNames(MODULE, childModule);
            for (ConfigPath child : childs) {
                File file = child.getFile();
                long lastModified = file.lastModified();
                ConnectDto connect = new ConnectDto(childModule, file.getName(), new Date(lastModified));
                connects.add(connect);
            }
        }
        return connects;
    }

    /**
     * 删除连接文件
     * @param module
     * @param connName
     */
    public void dropConnect(String module, String connName) {
        fileManager.dropConfig(MODULE,module+"/"+connName);
    }
}
