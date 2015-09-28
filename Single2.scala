import akka.actor._;
//import akka.routing.RoundRobinPool;
import scala.collection.mutable.ListBuffer;
import akka.actor.Actor
import java.security.MessageDigest
import akka.dispatch.Foreach
import scala.util.control.Breaks;
import scala.util.Random
import java.io._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingReceive
import scala.util.control.Breaks
import java.security.MessageDigest
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.ArrayBuffer
import akka.actor.Terminated
import akka.actor.actorRef2Scala

object Single2 {

 
  sealed trait HexStringGeneration
  case object Calculate extends HexStringGeneration
  case object StartWorker extends HexStringGeneration
  case object Stop extends HexStringGeneration
  case object GiveWork extends HexStringGeneration
  case class Work(randomString: String, zeroCountReq: Int) extends HexStringGeneration
  case class MinnedHexCode(minnedHexCodeString: String) extends HexStringGeneration
  case class StopAndPrintExecutionSummary(noOfMinnedStrings: Int, duration: Long) extends HexStringGeneration
  //case class Start(nrOfStrings: Int, nrOfWorkers: Int, zeroCountReq: Int, listener: ActorRef) extends HexStringGeneration

  


  class Worker extends Actor {
	  var randomString = 0 
	  	  
	  def generateHexCode(randomString: String, zeroCountReq:Int): String = { 
	        var hashValue = encrypt(randomString)
	        var zeroFound:Int=checkZeroCount(hashValue)
	        if(zeroFound==zeroCountReq){                            
	              var combined:String=randomString + "\t" + hashValue                  
	              hashValue = combined
	           }
	        else{
	        	hashValue = null
	        }    
	        hashValue  
	  }     
	   
	  def encrypt(randomString: String): String = {
	    
	        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
	    	sha256.update(randomString.getBytes("UTF-8"));    
	    	val shaString: Array[Byte] = sha256.digest    
	    	getHexString(shaString)
	  }
	  
	  def getHexString(messageDigest: Array[Byte]): String = {
	     
	    	val hexString: StringBuffer = new StringBuffer    
	    	for(i<-0 to messageDigest.length-1){     
	              hexString.append(Integer.toString((messageDigest(i) & 0xff) + 0x100, 16).substring(1));        
	    	}     
	    	hexString.toString
	  }
	    
	  def checkZeroCount(a:String):Int = {  
	     
	  		var zeroCount:Int=0;
	    	val loop = new Breaks;
	    	loop.breakable {
	        	  for(j<- 0 to a.length())
	        	  {
	                    if(a(j)== '0'){
	              	    zeroCount+=1
	              }
	              else
	               		loop.break;
	        }    
	    	}        
	    	return zeroCount
	  }
	  
	    def receive = {
	    	case StartWorker =>{
	    		println("Worker Start")
	    		sender ! GiveWork
	    	}

	    	case Work(randomString, zeroCountReq) => {
	    		var minnedHexCodeString = generateHexCode(randomString, zeroCountReq)
	    		if (minnedHexCodeString != null)
	    			sender ! MinnedHexCode(minnedHexCodeString)
	    		sender ! GiveWork
	    	}

	    	case Stop =>{
	    		context.stop(self)
	    	}
	    }
  }
 
 


object Master {
    
    case class Start(nrOfStrings: Int, nrOfWorkers: Int, zeroCountReq: Int, listener: ActorRef)
    
    case object NewRemoteWorker
  }





  class Master()
    extends Actor {
    	import Master._
    	var nrOfStrings: Int =0
    	var nrOfWorkers: Int = 0
    	var zeroCountReq: Int = 0
    	var listener: ActorRef = null
      
	    val start: Long = System.currentTimeMillis
	    var minnedHexCodeString = 0
	    var minnedHexCodes = new ListBuffer[String]()
	    var stringsLeft = 0
	    var closedWorkers: Int = 0;
	    def randomStringf():String = {
	    	var randNum = (Random.nextInt(15))+5	
	    	var randString = Random.alphanumeric.take(randNum).mkString
	    	return randString.toString()
	    }     
	 
	   	 
	    def receive = {

	    	case Start(nrOfStrings2, nrOfWorkers2, zeroCountReq2, listener2) => {
	    		nrOfStrings = nrOfStrings2
	    		nrOfWorkers = nrOfWorkers2
	    		zeroCountReq = zeroCountReq2
	    		listener = listener2
	    		println("start")
	    		for(i <- 0 to nrOfWorkers-1){
					val worker = context.actorOf(Props[Worker], name = "worker:%s".format(i))
					worker ! StartWorker
				}

	    	}	

	    	case NewRemoteWorker =>{

	    		nrOfWorkers = nrOfWorkers + 1
	    		sender ! StartWorker
	    	}

	    	case GiveWork =>{
	    		if(stringsLeft < nrOfStrings){
	    			var randomString = "41403921;" + randomStringf() 
	    			sender ! Work(randomString, zeroCountReq)
	    			stringsLeft = stringsLeft + 1
	    		}
	    		else{
	    			sender ! Stop
	    			closedWorkers = closedWorkers + 1;
	    			println("closedWorkers: %s".format(closedWorkers))
	    			if(closedWorkers == (nrOfWorkers)){
	    				println("Work Done and All Workers Closed")
	    				var duration = (System.currentTimeMillis - start)
	    					listener ! StopAndPrintExecutionSummary(minnedHexCodes.size, duration)
	    			}
	    		}
	    	}

	    	case MinnedHexCode(minnedHexCodeString) =>{
	    		  minnedHexCodes += minnedHexCodeString
	    		  println(minnedHexCodeString)
	    	}
	  
	    }
 
  }
 
 





  class Listener extends Actor {
  		var size = 0
  		var duration = 0
	    def receive = {
	      	case StopAndPrintExecutionSummary(size, duration) => {
	      	println("\n\tNumber of Minned Strings : %s\n\tCalculation time: \t%s ms".format(size, duration))
	      	context.system.shutdown()
	        }
	    }
  }
 
 
 

  def main(args: Array[String]) {
  
	var nrOfWorkers = 8// no of Worker Actors to create on invoking the program.
    var zeroCountReq = 0
    var ipAddress = ""
    var nrOfStrings = 100000000

    // exit if argument not passed as command line param
    if (args.length < 1) {
      println("Invalid no of args")
      System.exit(1)
    } 
    else 
    if (args.length == 1) {
      args(0) match {
        case s: String if s.contains(".") =>
          ipAddress = s
          val remoteSystem = ActorSystem("RemoteBitCoinSystem", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 0 } } } } } """)))
          val worker = remoteSystem.actorOf(Props(new Worker()), name = "Worker")
          //val watcher = remoteSystem.actorOf(Props(new Watcher()), name = "Watcher")

          //watcher ! Watcher.WatchMe(worker)
          //var masterRemote = new Master
          val master = remoteSystem.actorSelection("akka.tcp://BitCoinSystem@" + ipAddress + ":2552/user/Master")
          master.tell(Master.NewRemoteWorker, worker)
   

        case s: String =>
          zeroCountReq = s.toInt
          //var masterServer = new Master
          val system = ActorSystem.create("BitCoinSystem", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 2552 } } } } } """)))
          val listener = system.actorOf(Props[Listener], name = "listener")
          val master = system.actorOf(Props(new Master()), name = "Master")
          master.tell(Master.Start(nrOfStrings, nrOfWorkers,zeroCountReq, listener), master)

        case _ => System.exit(1)
             }
    }



   
  }
}