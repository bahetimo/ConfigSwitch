package com.bteamore.configswitch.repo;

import java.nio.file.Path;

public interface IConfigTarget {
    //Global Repo
    String home = System.getProperty("user.home");
    Path globalDir = Path.of(home, "AppData", "Roaming", ".minecraft", "configswitch","global");
    Path commonDir = Path.of(globalDir.toString(),"common");


    Path activeFile();
    Path globalFile();
    Path backupFile();
}
