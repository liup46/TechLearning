## ZygoteInit 过程
```groovy
ZygoteInit.main{
        Os.setpgid(0, 0);
        RuntimeInit.preForkInit()->RuntimeInit.enableDdms();
        preload{
            preloadClasses();
            preloadResources();
            maybePreloadGraphicsDriver();
            WebViewFactory.prepareWebViewInZygote();
        }
        
        zygoteServer = new ZygoteServer(isPrimaryZygote);
        Runnable r = forkSystemServer(abiList, zygoteSocketName, zygoteServer){
            //// command --setuid=1000","--setgid=1000, com.android.server.SystemServer
            pid = Zygote.forkSystemServer(){ //0 if this is the child, pid of the child if this is the parent,
                 return nativeForkSystemServer()           
            } 
            if (pid == 0) {
                return  handleSystemServerProcess{
                    return ZygoteInit.zygoteInit{
                        RuntimeInit.commonInit{
                                //// Bring up crash dialog, wait for it to be dismissed
                                Thread.setDefaultUncaughtExceptionHandler(new KillApplicationHandler(loggingHandler))
                                -->AMS.handleApplicationCrash
                        }
                        return findStaticMain() //com.android.server.SystemServer
                    }
                }
            }
        }; 
        if (r != null) { //{r == null} in the parent (zygote) process, and {@code r != null} in the child (system_server) process.
            r.run();//com.android.server.SystemServer.main
            return;
        }
        
        caller = zygoteServer.runSelectLoop(abiList){
            while(true){
                if (pollIndex == 0) {
                    // Zygote server socket，接收客户端连接
                    ZygoteConnection newPeer = acceptCommandPeer(abiList);
                    peers.add(newPeer);
                    socketFDs.add(newPeer.getFileDescriptor());   
                }else if (pollIndex < usapPoolEventFDIndex) {{
                    //处理客户端发送的创建进程的请求数据
                    final Runnable command = connection.processCommand(this, multipleForksOK){
                        pid = Zygote.forkAndSpecialize(){ ////startChildZygote =false 
                            nativeForkAndSpecialize                    
                        }    
                        handleParentProc{
                            //往客户端发送pid
                            mSocketOutStream.writeInt(pid);
                            mSocketOutStream.writeBoolean(usingWrapper);                        
                        }
                        return null                                                                                 
                    }                 
                }           
            }
            
        }
        if (caller != null) {
            //We're in the child process and have exited the select loop. Proceed to execute the command
            caller.run();
        }

}
```

## SystemServer执行流程
SystemServer.main{
    SystemServer().run(){
        startBootstrapServices{
            ActivityTaskManagerService atm = mSystemServiceManager.startService(ActivityTaskManagerService.Lifecycle.class).getService();
            .mActivityManagerService = ActivityManagerService.Lifecycle.startService(mSystemServiceManager, atm);
            .mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
            .mActivityManagerService.setInstaller(installer);
        }
        startCoreServices(t);
        startOtherServices{
            wm = WindowManagerService.main(context, inputManager, !mFirstBoot, mOnlyCore,
                    new PhoneWindowManager(), mActivityManagerService.mActivityTaskManager){
                        _sInstance = new WindowManagerService(context, im, showBootMsgs, onlyCore, policy,atm, displayWindowSettingsProvider, transactionFactory, surfaceFactory,
                        surfaceControlFactory){
                            mRoot = new RootWindowContainer(this);
                        }
                    }
            ServiceManager.addService(Context.WINDOW_SERVICE, wm, /* allowIsolated= */ false,DUMP_FLAG_PRIORITY_CRITICAL | DUMP_FLAG_PROTO);
            ServiceManager.addService(Context.INPUT_SERVICE, inputManager,/* allowIsolated= */ false, DUMP_FLAG_PRIORITY_CRITICAL);
            .mActivityManagerService.setWindowManager(wm){
                .mWindowManager = wm;
                .mActivityTaskManager.setWindowManager(wm);
            }
            wm.onInitReady();
        }
    }
}

//Todo
ZygoteHooks, RuntimeHooks, nativeForkSystemServer, nativeForkAndSpecialize