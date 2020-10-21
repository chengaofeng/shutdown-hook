## java shutdownhook

在java程序停止前，我们可能会需要一些清理工作，如关闭数据库连接池，执行一些反注册等。Runtime的addShutdownHook方法给我们提供了这样一个机制，通过这个方法，我们可以告诉JVM，在收到停止信号时，执行一些我们自定义的逻辑

```
 /**
     * Registers a new virtual-machine shutdown hook.
     *
     * <p> The Java virtual machine <i>shuts down</i> in response to two kinds
     * of events:
     *
     *   <ul>
     *
     *   <li> The program <i>exits</i> normally, when the last non-daemon
     *   thread exits or when the <tt>{@link #exit exit}</tt> (equivalently,
     *   {@link System#exit(int) System.exit}) method is invoked, or
     *
     *   <li> The virtual machine is <i>terminated</i> in response to a
     *   user interrupt, such as typing <tt>^C</tt>, or a system-wide event,
     *   such as user logoff or system shutdown.
     *
     *   </ul>
     *
     * <p> A <i>shutdown hook</i> is simply an initialized but unstarted
     * thread.  When the virtual machine begins its shutdown sequence it will
     * start all registered shutdown hooks in some unspecified order and let
     * them run concurrently.  When all the hooks have finished it will then
     * run all uninvoked finalizers if finalization-on-exit has been enabled.
     * Finally, the virtual machine will halt.  Note that daemon threads will
     * continue to run during the shutdown sequence, as will non-daemon threads
     * if shutdown was initiated by invoking the <tt>{@link #exit exit}</tt>
     * method.
     * 
     * <p> In rare circumstances the virtual machine may <i>abort</i>, that is,
     * stop running without shutting down cleanly.  This occurs when the
     * virtual machine is terminated externally, for example with the
     * <tt>SIGKILL</tt> signal on Unix or the <tt>TerminateProcess</tt> call on
     * Microsoft Windows.  The virtual machine may also abort if a native
     * method goes awry by, for example, corrupting internal data structures or
     * attempting to access nonexistent memory.  If the virtual machine aborts
     * then no guarantee can be made about whether or not any shutdown hooks
     * will be run. <p>
     * 
     * @see #removeShutdownHook
     * @see #halt(int)
     * @see #exit(int)
     * @since 1.3
     */
     public void addShutdownHook(Thread hook)
```
* 此方法在程序正常终止或者jvm收到中断```interrupt```、停止信号			```terminate```时被触发
* 一个程序可以注册多个shutdown hook，当JVM开始停止时，这些shutdown hooks会同时执行，相互之间没有次序


|序号|执行命令|结果|说明|
|---|---|---|---|
|1. |kill -9 |不能触发|发送的是SIGKILL|
|2. |kill  |触发|默认的是kill -15 发送SIGERM|
|3. |ctrl+c|触发|发送的是SIGINT|
|4. | 正常结束 |触发||
|5. | oom |触发||




## docker 容器内部

将java程序做成docker镜像，以容器形式执行时，我们不能直接给容器内部的java进程发送信号，此时只能通过docker命令来操作正在运行的容器。根据[docker stop](https://docs.docker.com/engine/reference/commandline/stop/)命令的描述，

```
The main process inside the container will receive SIGTERM, and after a grace period, SIGKILL.
```
docker只会给容器内的主进程发送信号，所以为了使java进程能收到停止信号，触发shutdown hooks，java 进程在容器内只能作为主进程（1号进程）运行。

可以通过以下方式让Java进程作为主进程在容器中运行。  

1. 在Dockerfile中通过 ```CMD```作为容器启动的默认命令,如：

	```
	FROM openjdk:8u212-jdk-alpine
	ADD ***.jar /home
	
	
	WORKDIR /home
	
	CMD java -jar ***.jar
	 
	```
2. 在Dockerfile中用exec格式的```ENTRYPOINT```作为容器启动的默认命令，在```ENTRYPOINT```对应的脚本内部，用```exec```启动java程序，如：
	
	Dockerfile:
	
	```
	FROM openjdk:8u212-jdk-alpine

	ADD spring-boot-shutdownhook-1.0-SNAPSHOT.jar /home
	
	COPY docker-entrypoint.sh /home/docker-entrypoint.sh
	WORKDIR /home
	
	RUN chmod +x docker-entrypoint.sh
	ENTRYPOINT ["./docker-entrypoint.sh"]
	
	```
	docker-entrypoint.sh:
	
	```
	#!/bin/sh
	
	#do something
	
	exec java -jar spring-boot-shutdownhook-1.0-SNAPSHOT.jar
	
	```
	* ***linux exec 命令的意思是在当前进程内执行，并且exec命令后面的 指令就不在执行了***
	* ```ENTRYPOINT command param1 param2``` 和 ```ENTRYPOINT ["/bin/sh", "param1"]``` ***都是shell模式，pid 为1的进程都是shell，不能使Java进程收到停止信号***
	
  |序号|执行命令|结果|说明|
  |---|---|---|---|
  |1. |docker rm -f |不能触发|直接发送SIGKILL|
  |2. |docker stop |触发|
  |3. |docker stack rm |触发|
  |4. |docker service rm |触发|
  |5. |docker service scale |触发|缩减实例个数的情况下|
  |6. |docker service update |触发|造成实例停止的更新|



## kubernetes pod [Termination of Pods](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)

1. If one of the Pod's containers has defined a preStop hook, the kubelet runs that hook inside of the container. If the preStop hook is still running after the grace period expires, the kubelet requests a small, one-off grace period extension of 2 seconds.  

	> Note: If the preStop hook needs longer to complete than the default grace period allows, you must modify terminationGracePeriodSeconds to suit this.

1. The kubelet triggers the container runtime to send a <font color=red>TERM </font>signal to <font color=red>process 1</font> inside each container.

	> Note: The containers in the Pod receive the TERM signal at different times and in an arbitrary order. If the order of shutdowns matters, consider using a preStop hook to synchronize.
	
* 只有容器中的1号进程能收到SIGTERM信号，所以为了在k8s环境下，使Java进程执行shutdown hooks，需保证在容器中的Java进程是主进程
* 在k8s环境下，还可以通过preStop这个hook来在主进程收到TERM之前做一些事情，如果我们的Java进程在容器中不是主进程，在k8s环境下，我们可以通过如下的preStop来触发Java进程的shutdown hook

	```
	
	***
	containers:
	- image: myimage:test
	  lifecycle:
	    preStop:
	      exec:
	        command: ["/bin/sh","-c","ps|grep java|grep -v grep| awk '{ print $1 }' | xargs -I{} kill {}]
	***
	
	```
	* 在preStop这个hook中，通过```kill ${java 进程PID``` ```(kill 默认发送 15 SIGTERM 信号）```

## 补充
1. 当Java进程在容器中是1号进程时，虽然能收到```SIGTERM```信号，自动执行shutdown hooks，但是，利用 jmap、jstack等工具对1号进程（Java进程）进行分析时，会出现如下错误

	```
	chengaofeng@chengaofeng target % docker exec -it b6a45781b81f sh
		
	/home # ps
	PID   USER     TIME  COMMAND
	    1 root      0:22 java -jar spring-boot-shutdownhook-1.0-SNAPSHOT.jar
	   37 root      0:00 sh
	   42 root      0:00 ps
	/home # jmap -dump:format=b,file=dump.bin 1
	1: Unable to get pid of LinuxThreads manager thread
	
	```

1. 如果想让Java进程既不是1号进程，也要能收到信号，可以利用[tini](https://github.com/krallin/tini/)来实现 ,通过让tini运行在1号进程，Java作为tini的子进程来实现

	Dockerfile:
	
	```
	FROM openjdk:8u212-jdk-alpine

	ADD spring-boot-shutdownhook-1.0-SNAPSHOT.jar /home
	
	COPY docker-entrypoint.sh /home/docker-entrypoint.sh
	WORKDIR /home
	
	RUN chmod +x docker-entrypoint.sh
	RUN apk add --no-cache tini
	ENTRYPOINT ["/sbin/tini", "--","./docker-entrypoint.sh"]
	
	```
	docker-entrypoint.sh:
	
	```
	#!/bin/sh

	echo "hello"
	exec java -jar spring-boot-shutdownhook-1.0-SNAPSHOT.jar

	```
	
	启动后进入容器
	
	```
	chengaofeng@chengaofeng target % docker exec -it 7b04dd056973 sh
	/home # ps
	PID   USER     TIME  COMMAND
	    1 root      0:00 /sbin/tini -- ./docker-entrypoint.sh
	    7 root      0:21 java -jar spring-boot-shutdownhook-1.0-SNAPSHOT.jar
	   37 root      0:00 sh
	   43 root      0:00 ps
	/home # jstack 7
	2020-10-21 03:31:36
	Full thread dump OpenJDK 64-Bit Server VM (25.212-b04 mixed mode):
	
	"Attach Listener" #30 daemon prio=9 os_prio=0 tid=0x000056519338b800 nid=0x38 waiting on condition [0x0000000000000000]
	   java.lang.Thread.State: RUNNABLE
	
	```
	
	
	执行```docker stop``` 命令停止容器对应的日志
	
	```
	2020-10-21 03:34:16.845  INFO 7 --- [       Thread-0] o.example.shutdownhook.ShutdownHookApp   : app shutdown hook executed
	
	```
	
	对应的代码
	
	```
	@SpringBootApplication
	@Slf4j
	public class ShutdownHookApp {
	
	    public static void main(String[] args) {
	        Runtime.getRuntime().addShutdownHook(new Thread(()->{
	
	           log.info("app shutdown hook executed");
	        }));
	
	        SpringApplication.run(ShutdownHookApp.class, args);
	    }
	}
	```
	
	* 需要让Java进程时tini的直接子进程

## 总结
使容器内Java进程能收到停止信号有以下三种方式
	
1. 通过 ```CMD java -jar ``` 直接运行
2. 以exec格式启动 ```ENTRYPOINT```,在 ```ENTRYPOINT```对应的脚本中，以 ```exec java -jar``` 形式启动java进程
3. 以 exec 形式启动ENTRYPOINT，command用```tini```，在 ```ENTRYPOINT```对应的脚本中，以 ```exec java -jar``` 形式启动java进程

* 其中前两种都是让Java进程作为一号进程运行,第三种以tini作为一号进程，Java作为tini的子进程
	
	
	
	
