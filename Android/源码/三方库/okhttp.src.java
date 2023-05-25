
OkHttpClient


RealCall{
    execute()

    enqueue(responseCallback){
        client.dispatcher.enqueue(AsyncCall(responseCallback)){
            //Class Dispatcher 
            promoteAndExecute{
                synchronized(this) {
                    val i = readyAsyncCalls.iterator()
                    while (i.hasNext()) {
                      val asyncCall = i.next()
                        //检查正在执行的最大请求数和单host最大请求数，  var maxRequests = 64 ， var maxRequestsPerHost = 5
                      if (runningAsyncCalls.size >= this.maxRequests) break // Max capacity.
                      if (asyncCall.callsPerHost.get() >= this.maxRequestsPerHost) continue // Host max capacity.
              
                      i.remove()
                      asyncCall.callsPerHost.incrementAndGet()
                      executableCalls.add(asyncCall)
                      runningAsyncCalls.add(asyncCall)
                    }
                  }
              
                  for (i in 0 until executableCalls.size) {
                    val asyncCall = executableCalls[i]
                    asyncCall.executeOn(executorService)
                  }
            }
        }
    }
}


//重试(while循环）和根据responseCode处理转发请求
RetryAndFollowUpInterceptor(client){
    intercept(chain: Interceptor.Chain){
        while (true) { //这里默认会不停的重试, 当timeout
            response = realChain.proceed(request)
            followUpRequest{
                when (responseCode) {
                    HTTP_PROXY_AUTH (407)->client.proxyAuthenticator.authenticate
                    HTTP_UNAUTHORIZED(401)->client.authenticator.authenticate
                    HTTP_PERM_REDIRECT(308), HTTP_TEMP_REDIRECT(307), HTTP_MULT_CHOICE(300), HTTP_MOVED_PERM(301), HTTP_MOVED_TEMP(302), HTTP_SEE_OTHER(303)
                        ->buildRedirectRequest()
                    HTTP_CLIENT_TIMEOUT(408)
                    HTTP_UNAVAILABLE(503)
                        return userResponse.request
            }

    }
}

// 设置请求头如Content-Type， gzip，Cookie， 同时处理返回body 如gzip, cookie
BridgeInterceptor(client.cookieJar)

//检查request的cachecontrol 和是否有缓存，同时处理response的cache 设置或缓存reponse
CacheInterceptor(client.cache){
    //前提： 1. okhttpClient 设置了cache。2. request 设置了cachecontrol且不是noStore， 3.cache只保存get请求
    // 4.如果设置了cachecontrol是onlyIfCached(do not use network)， 而且没有cache 过，则会返回Response.code =HTTP_GATEWAY_TIMEOUT(504)； 如果有缓存，直接返回缓存的数据
    //5.如果网络返回的code = HTTP_NOT_MODIFIED(304),返回缓存数据。
    // 6.其他情况返回 网络返回的数据，如果request和response 符合条件1.2.3,则会缓存reponse到本地文件
    // cache key 根据request url 进行md5并转行成hex字符串，
    DiskLruCache
}

ConnectInterceptor{
//连接复用
 //ConnectionPool(maxIdleConnections =5,keepAliveDuration= 5,TimeUnit.MINUTES) //5分钟

    call.initExchange{

        //socket 是否健康：1.没有关闭，2.读和写io 没有关闭. 3.设置  soTimeout = 1(1ms) 会报SocketTimeoutException
        //RealConnectionPool连接池的复用，遍历所有连接RealConnection.isEligible找到合格的连接：1.当前连接没有分配，2.当前连接address与新请求的adress一致（包括dns,protocols,connectionSpecs,proxy,url.port等）
            3.host必须一致. 4. must be HTTP/2. 5.routes must share an IP address。6.This connection's server certificate's must cover the new host.7.Certificate pinning must match the host

    }
}

//发送请求数据，处理socket返回数据
CallServerInterceptor