package com.sanri.tools.modules.zookeeper.controller;

import com.sanri.tools.modules.zookeeper.dtos.ZooNodeACL;
import com.sanri.tools.modules.zookeeper.service.ZookeeperService;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/zookeeper")
public class ZookeeperController {

    @Autowired
    private ZookeeperService zookeeperService;

    @GetMapping("/childrens")
    public List<String> childrens(String connName, String path) throws IOException {
        return zookeeperService.childrens(connName,path);
    }

    @GetMapping("/meta")
    public Stat meta(String connName, String path) throws IOException{
        return zookeeperService.meta(connName,path);
    }

    @GetMapping("/acls")
    public List<ZooNodeACL> acls(String connName, String path) throws IOException{
        return zookeeperService.acls(connName,path);
    }

    @GetMapping("/readData")
    public Object readData(String connName,String path,String deserialize) throws IOException{
        return zookeeperService.readData(connName,path,deserialize);
    }

    @PostMapping("/deleteNode")
    public void deleteNode(String connName,String path) throws IOException{
        zookeeperService.deleteNode(connName,path);
    }

    @PostMapping("/writeData")
    public void writeData(String connName,String path,String data) throws IOException {
        zookeeperService.writeData(connName,path,data);
    }
}
