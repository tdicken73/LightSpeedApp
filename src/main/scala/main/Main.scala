package main
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import akka.io.IO
import akka.pattern.ask
import spray.can.Http
import spray.util._
import spray.http._
import akka.event._
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import scala.concurrent.Future
import akka.actor.ActorSelection.toScala
import akka.actor.actorRef2Scala
import akka.cluster.ClusterEvent.MemberUp
import spray.http.ContentType.apply
import scala.io.Source

//#imports

//#messages
case class TransformationJob(count: Integer,text: String)
case class TransformationResult(text: String)
case class JobFailed(reason: String, job: TransformationJob)
case object BackendRegistration
//#messages



object TransformationFrontend {
  def main(args: Array[String]): Unit = {

    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(Props[TransformationFrontend], name = "frontend")
    implicit val actSystem = ActorSystem()
    IO(Http) ! Http.Bind(frontend, interface = "localhost", port = 8080)
  
  }
}

//#frontend
class TransformationFrontend extends Actor with SprayActorLogging {
  import context.dispatcher

  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0
  val duration = 30 seconds
  implicit val timeout = Timeout(duration)

   //Naive round robin routing
  var currentWorker = -1
  def nextWorker = {
    if (currentWorker >= backends.size - 1) {
      currentWorker = 0
    } else {
      currentWorker = currentWorker + 1
    }
    backends(currentWorker)
  }

  def index(s: Int) = {  
	/*
    HttpResponse(
    entity = HttpEntity(`text/html`,
    		Source.fromFile("index.html").mkString
      ) 
  )
  */ 
  HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
  <head>
    <meta charset="utf-8"></meta>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <meta name="description" content=""></meta>
    <meta name="author" content=""></meta>
    <link rel="shortcut icon" href="assets/img/favicon.png"></link>
    <link href="//netdna.bootstrapcdn.com/font-awesome/4.0.3/css/font-awesome.css" rel="stylesheet"></link>
    <title>LightSpeed</title>

    <!-- Bootstrap core CSS -->
    <link href="http://www.corydobson.com/LightSpeed/assets/css/bootstrap.css" rel="stylesheet"></link>

    <!-- Custom styles for this template -->
    <link href="http://www.corydobson.com/LightSpeed/assets/css/main.css" rel="stylesheet"></link>

    <!-- Fonts from Google Fonts -->
	<link href='http://fonts.googleapis.com/css?family=Lato:300,400,900' rel='stylesheet' type='text/css'></link>
    
    <!-- HTML5 shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
      <script src="https://oss.maxcdn.com/libs/respond.js/1.3.0/respond.min.js"></script>
    <![endif]-->
  </head>
        <body>
    
    	   <div class="navbar navbar-default navbar-fixed-top">
      <div class="container">
        <div class="navbar-header">
          <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
          </button>
          <a class="navbar-brand" href="#"><img src="http://www.corydobson.com/LightSpeed/assets/img/bolt2.png"  width="17" vertical-align="text-bottom" alt=""> <b>LIGHTSPEED</b></img></a>
        </div>
        <div class="navbar-collapse collapse">
          <ul class="nav navbar-nav navbar-right">
          	<li><a href="lightspeed/test"><b>APP TEST DASHBOARD</b></a></li>
            <li><a href="#about"><b>ABOUT</b></a></li>
            <li><a href="#team"><b>THE TEAM</b></a></li>
            <li><a href="http://www.github.com/corydobson/LightspeedApp"><b>SOURCE REPO</b></a></li>
          </ul>
        </div><!--/.nav-collapse -->
      </div>
    </div>

	<div id="headerwrap">
		<div class="container">
			<div class="row">
				<div class="col-lg-6">

					<h1>Welcome to LightSpeed.</h1>
						<h3>A distributed Key-Value Store powered by the Symas Lightning DB, and Akka Clusters</h3>
    			
    				     <h1> Akka Clusters Avaliable! </h1>
    					<h3>{s}</h3> <h3>Akka cluster workers available with Lightning DB installed.</h3>

				</div><!-- /col-lg-6 -->
				<div class="col-lg-6">
					<img class="img-responsive" src="http://www.corydobson.com/LightSpeed/assets/img/computers.png" alt=""></img>
				</div>
				
			</div><!-- /row -->
		</div><!-- /container -->
	</div><!-- /headerwrap -->
	
	
	<div class="container" id ="about">
		<div class="row mt centered">
			<div class="col-lg-6 col-lg-offset-3">
				<h1>Your data.<br/> Lightning Fast.</h1>
				<h3>LightSpeed is a Key-Value Store, built with powerful tools to get your data moving fast.</h3>
			</div>
		</div><!-- /row -->
		
		<div class="row mt centered">
			<div class="col-lg-4">
				<a href = "http://symas.com/mdb/" target="_blank"><img src="http://www.corydobson.com/LightSpeed/assets/img/ser01.png" width="200" alt=""> </img></a>
				<h4>1 - Symas Lightning Database Layer</h4>
				<p>LightSpeed is powered by Symas Corp's <a href = "http://symas.com/mdb/" target="_blank">Lightning Memory Map Database</a>. This ultra-fast, ultra-compact key-value embedded data store <a href = "http://symas.com/mdb/microbench/" target="_blank">outperforms</a> other popular key value software solutions such as BerkeleyDB, Kyoto TreeDB, and even Google's LevelDB.  LightSpeed utilizes Lightning, and sets it up as a distributed database model - increasing performance and allowing for it to solve Big Data problems.</p>
			</div><!--/col-lg-4 -->

			<div class="col-lg-4">
				<a href = "http://spray.io/" target="_blank"> <img src="http://www.corydobson.com/LightSpeed/assets/img/ser02.png"  width="120" alt=""></img></a>
				<h4>2 - Spray.io RESTful API Routing</h4>
				<p>The <a href = "http://spray.io/" target="_blank">Spray.io RESTful API</a> handles all of LightSpeed's HTTP services - allowing for it to run completely in browser over the web. This simple <a href= "http://codeplanet.io/principles-good-restful-api-design/" target="_blank">RESTful</a> HTTP service comes with a small, embedded and super-fast HTTP server called spray-can, which is fully asynchronous and can handle thousands of concurrent connections. This allows for LightSpeed to handle multiple users on any size device at once.</p>

			</div><!--/col-lg-4 -->

			<div class="col-lg-4">
				<a href = "http://akka.io/" target="_blank"> <img src="http://www.corydobson.com/LightSpeed/assets/img/ser03.png" width="86" alt=""></img></a>
				<h4>3 - Akka Actor System Cluters </h4>
				<p>Akka is a toolkit and runtime for building highly concurrent, distributed, and fault tolerant event-driven applications on the JVM. LightSpeed utilizes Akka by creating Akka Clusters that are virtual CPU's that can communicate with each other using Actor messages. Each cluster runs its own copy of Lightning DB, and has its own subsection of data to manage. </p>
			</div><!--/col-lg-4 -->
		</div><!-- /row -->
	</div><!-- /container -->
	
	<div class="container">
		<hr></hr>
		<hr></hr>
	</div><!-- /container -->
	
	<div class="container">
		<div class="row mt centered">
			<div class="col-lg-6 col-lg-offset-3">
				<h1>LightSpeed in Action.</h1>
				<h3>See how LightSpeed distributes data over multiple clusters below.</h3>
			</div>
		</div><!-- /row -->
	
		<div class="row mt centered">
			<div class="col-lg-6 col-lg-offset-3">
				<div id="carousel-example-generic" class="carousel slide" data-ride="carousel">
				  <!-- Indicators -->
				  <ol class="carousel-indicators">
				    <li data-target="#carousel-example-generic" data-slide-to="0" class="active"></li>
				    <li data-target="#carousel-example-generic" data-slide-to="1"></li>
				    <li data-target="#carousel-example-generic" data-slide-to="2"></li>
				  </ol>
				  <!-- Wrapper for slides -->
				  <div class="carousel-inner">
				    <div class="item active">
				      <img src="assets/img/p01.png" alt=""></img>
				    </div>
				    <div class="item">
				      <img src="assets/img/p02.png" alt=""></img>
				    </div>
				    <div class="item">
				      <img src="assets/img/p03.png" alt=""></img>
				    </div>
				  </div>
				</div>			
			</div><!-- /col-lg-8 -->
		</div><!-- /row -->
	</div>
	
	<div class="container">
		<hr></hr>
		<hr></hr>
	</div><!-- /container -->

	<div class="container" id ="team">
		<div class="row mt centered">
			<div class="col-lg-6 col-lg-offset-3">
				<h1>Our awesome team of <br/>Computer Science rockstars.</h1>
				<h3>We are a team of West Virginia University Computer Science majors with the best academic advisor around.</h3>
			</div>
		</div><!-- /row -->
		
		<div class="row mt centered">
			<div class="col-lg-4">
				<img class="img-circle" src="http://www.corydobson.com/LightSpeed/assets/img/cory.jpg" width="140" alt=""></img>
				<h3>Cory Dobson</h3>
				<h4>Lead Engineer and Team Lead</h4>
				<p>Cory leads LightSpeed's core development and web integration, along with leading the team and creating the LightSpeed brand graphics and UI.</p>
			</div><!--/col-lg-4 -->

			<div class="col-lg-4">
				<img class="img-circle" src="http://www.corydobson.com/LightSpeed/assets/img/bryan.jpg" width="140" alt=""></img>
				<h3>Bryan Turek</h3>
				<h4>Full Stack Engineer</h4>
				<p>Bryan focuses on getting LightSpeed onto the web, by integrating the user interface with the back-end cluster manager that controls all of LightSpeed's data.</p>
			</div>
			<div class="col-lg-4">
				<img class="img-circle" src="http://www.corydobson.com/LightSpeed/assets/img/joey.jpg" width="140" alt=""></img>
				<h3>Joey Dicerchio</h3>
				<h4>Integration Testing</h4>
				<p>Joey leads LightSpeed's blackbox testing and QA, writing test cases and testing LightSpeed's core features on multiple machines.</p>
			</div>
		</div><!-- /row -->
			<div class="row mt centered">
			<div class="col-lg-4">
				<img class="img-circle" src="http://www.corydobson.com/LightSpeed/assets/img/casey.jpg" width="140" alt=""></img>
				<h3>Casey Hancock</h3>
				<h4>Engineer</h4>
				<p>Casey tackles problems within LightSpeed's core, test suite, and web frameworks.</p>
			</div>
			<div class="col-lg-4">
				<img class="img-circle" src="http://www.corydobson.com/LightSpeed/assets/img/chelsea.jpg" width="140" alt=""></img>
				<h3>Chelsea Skotnicki</h3>
				<h4>Testing and Documentation</h4>
				<p>Chelsea leads our project's documentation, along with working with Joey to test LightSpeed's features with unit and blackbox testing.</p>
			</div>
			<div class="col-lg-4">
				<img class="img-circle" src="http://www.corydobson.com/LightSpeed/assets/img/ray.jpg" width="140" alt=""></img>
				<h3>Professor Ray Morehead</h3>
				<h4>Academic Advisor</h4>
				<p>Professor Morehead advises the team, along with teaching multiple classes that laid the groundwork for LightSpeed, including CS440: Database Design, and CS493: Java Concurrency. Without Morehead's classes, this project would not be possible.</p>
				
			</div><!--/col-lg-4 -->
		</div><!-- /row -->
	</div><!-- /container -->
	
		
	<div class="container">
		<hr></hr>
		<hr></hr>
		<p class="centered">Site by the LightSpeed Team - 2014</p>
	</div>

    	</body>
      </html>.toString()
      ) 
    )
  }

  lazy val noWorkers = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>No workers available</p>
        </body>
      </html>.toString()
    )
  )

  def jobResponse(s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>Response: {s}</p>
        </body>
      </html>.toString()
    )
  )

  def lightSpeedResponse(i: Integer,s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
  <head>
    <meta charset="utf-8"></meta>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <meta name="description" content=""></meta>
    <meta name="author" content=""></meta>

    <title>LightSpeed Test Suite</title>

    <!-- Bootstrap core CSS -->
    <link href="http://corydobson.com/LightSpeed/testsuite/assets/css/bootstrap.css" rel="stylesheet"></link>

    <!-- Custom styles for this template -->
    <link href="http://corydobson.com/LightSpeed/testsuite/assets/css/main.css" rel="stylesheet"></link>
    <link href="http://corydobson.com/LightSpeed/testsuite/assets/css/font-awesome.min.css" rel="stylesheet"></link>

    <script src="https://code.jquery.com/jquery-1.10.2.min.js"></script>

  </head>
  <body>

    <!-- Fixed navbar -->
    <div class="navbar navbar-default navbar-fixed-top">
      <div class="container">
        <div class="navbar-header">
          <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
          </button>
          <a class="navbar-brand" href="/"><img src="http://www.corydobson.com/LightSpeed/assets/img/bolt2.png" width="30" alt=""></img></a>
        </div>
        <div class="navbar-collapse collapse">
          <ul class="nav navbar-nav navbar-right">
            <li class="active"><a href="/">HOME</a></li>
            <li class="active"><a href="http://www.github.com/corydobson/LightspeedApp">SOURCE REPO</a></li>
          </ul>
        </div><!--/.nav-collapse -->
      </div>
    </div>
	
	<div id="green">
		<div class="container">
			<div class="row">
				<div class="col-lg-5 centered">
					<img src="http://corydobson.com/media/work/lightspeedLogo.jpg" style="max-height:300px; margin-bottom:50px;"></img>
				</div>
				
				<div class="col-lg-7 centered">
					<br></br><br></br>
					<h3>Welcome to the LightSpeed Test Suite</h3>
					<p>From full books, to show tunes, to 80's metal, these "Lightning themed" tests examine LightSpeed's ability to analyze, distribute, and then output strings in the key/value store.</p>
				</div>
			</div>
		</div>
	</div>
	

	<div class="container">
		<div class="row centered mt grid">
			<h3>OUR LATEST TESTS</h3>
			<div class="mt"></div>
			<div class="col-lg-4">
				<a href="/lightspeed/test/greaseLightning"><img src="http://corydobson.com/LightSpeed/testsuite/assets/img/01.jpg"></img></a>
				<h3>24 Hours of Grease Lightning! 1.6 Million K/V Pairs </h3>
			</div>
			<div class="col-lg-4">
				<a href="/lightspeed/test/thunder"><img src="http://corydobson.com/LightSpeed/testsuite/assets/img/02.jpg" alt=""></img></a>
				<h3>Ten Days of Thunder Struck! 271,000 K/V Pairs</h3>
			</div>
			<div class="col-lg-4">
				<a href="#"><img src="http://corydobson.com/LightSpeed/testsuite/assets/img/03.jpg" alt=""></img></a>
				<h3>The Lightning Thief Book!</h3>
			</div>
		</div>
		
		<div class="row mt centered">
			<div class="col-lg-7 col-lg-offset-1 mt">
					<p class="lead">Tests are best performed in sync with the LightSpeed dev console and terminal windows open to watch the action!</p>
					<br></br>
			</div>
		</div>
	</div>
	<div id="f">
		<div class="container">
			<div class="row">
				<p>Crafted with <i class="fa fa-heart"></i> by the LightSpeed team.</p>
			</div>
		</div>
	</div>

    <!-- Bootstrap core JavaScript
    ================================================== -->
    <!-- Placed at the end of the document so the pages load faster -->
    <script src="http://corydobson.com/LightSpeed/testsuite/assets/js/bootstrap.js"></script>
  </body>
      </html>.toString()
    )
  )
  
   def lightSpeedThunderResponse(i: Integer,s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
           <head>
    <meta charset="utf-8"></meta>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <meta name="description" content=""></meta>
    <meta name="author" content=""></meta>
    <link rel="shortcut icon" href="assets/ico/favicon.png"></link>

    <title>LightSpeed - Thunderstruck!</title>

    <!-- Bootstrap core CSS -->
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/bootstrap.min.css" rel="stylesheet"></link>
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/font-awesome.min.css" rel="stylesheet"></link>


    <!-- Custom styles for this template -->
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/main.css" rel="stylesheet"></link>
    
    <link href='http://fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'></link>
    <link href='http://fonts.googleapis.com/css?family=EB+Garamond' rel='stylesheet' type='text/css'></link>
    
  </head>
  <body>


    <div class="container">
		<div class="row centered">
			<div class="col-lg-8 col-lg-offset-2 w">
				<h1>THUNDERSTRUCK!</h1>
			</div>
		</div>
		<div class="row centered">
    		<iframe width="640" height="390" src="http://www.youtube.com/embed/v2AC41dglnM?autoplay=1"></iframe>
		</div>
    </div><!-- /.container -->
    
    <div class="container">
    	<div class="row w centered">
    		<div class="col-lg-6 col-lg-offset-3">
    			<h3>Currently Parsing out 240 hours of Thunderstruck - Approximately 270,000 words as Key/Value Pairs!</h3>
    		</div>
    	</div>
    </div>


    <!-- Bootstrap core JavaScript
    ================================================== -->
    <!-- Placed at the end of the document so the pages load faster -->
    <script src="https://code.jquery.com/jquery-1.10.2.min.js"></script>
    <script src="assets/js/bootstrap.min.js"></script>


    <script type="text/javascript" src="assets/js/jquery.backstretch.min.js"></script>
    <script type="text/javascript">$.backstretch("http://www.corydobson.com/LightSpeed/grease/assets/img/bg2.jpg");</script>
  </body>
     
      </html>.toString()
    )
  )
  
  
  def lightSpeedGreaseResponse(i: Integer,s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
          <head>
    <meta charset="utf-8"></meta>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <meta name="description" content=""></meta>
    <meta name="author" content=""></meta>
    <link rel="shortcut icon" href="assets/ico/favicon.png"></link>

    <title>LightSpeed - Grease Lightning!</title>

    <!-- Bootstrap core CSS -->
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/bootstrap.min.css" rel="stylesheet"></link>
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/font-awesome.min.css" rel="stylesheet"></link>


    <!-- Custom styles for this template -->
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/main.css" rel="stylesheet"></link>
    
    <link href='http://fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'></link>
    <link href='http://fonts.googleapis.com/css?family=EB+Garamond' rel='stylesheet' type='text/css'></link>
    
  </head>
  <body>


    <div class="container">
		<div class="row centered">
			<div class="col-lg-8 col-lg-offset-2 w">
				<h1>Grease LIGHTNING!</h1>
			</div>
		</div>
		<div class="row centered">
    		<iframe width="640" height="390" src="http://www.youtube.com/embed/4l2tLIZHlBQ?autoplay=1"></iframe>
		</div>
    </div><!-- /.container -->
    
    <div class="container">
    	<div class="row w centered">
    		<div class="col-lg-6 col-lg-offset-3">
    			<h3>Currently Parsing out 24 hours of Grease Lightning - Approximately 1.6 Million words as Key/Value Pairs!</h3>
    		</div>
    	</div>
    </div>


    <!-- Bootstrap core JavaScript
    ================================================== -->
    <!-- Placed at the end of the document so the pages load faster -->
    <script src="https://code.jquery.com/jquery-1.10.2.min.js"></script>
    <script src="assets/js/bootstrap.min.js"></script>


    <script type="text/javascript" src="assets/js/jquery.backstretch.min.js"></script>
    <script type="text/javascript">$.backstretch("http://www.corydobson.com/LightSpeed/grease/assets/img/bg.jpg");</script>
  </body>
      </html>.toString()
    )
  )

  
   def lightSpeedThiefResponse(i: Integer,s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
          <head>
    <meta charset="utf-8"></meta>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <meta name="description" content=""></meta>
    <meta name="author" content=""></meta>
    <link rel="shortcut icon" href="assets/ico/favicon.png"></link>

    <title>LightSpeed - The Lightning Thief!</title>

    <!-- Bootstrap core CSS -->
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/bootstrap.min.css" rel="stylesheet"></link>
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/font-awesome.min.css" rel="stylesheet"></link>


    <!-- Custom styles for this template -->
    <link href="http://www.corydobson.com/LightSpeed/grease/assets/css/main.css" rel="stylesheet"></link>
    
    <link href='http://fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'></link>
    <link href='http://fonts.googleapis.com/css?family=EB+Garamond' rel='stylesheet' type='text/css'></link>
    
  </head>
  <body>


    <div class="container">
		<div class="row centered">
			<div class="col-lg-8 col-lg-offset-2 w">
				<h1>The Lightning Thief!</h1>
			</div>
		</div>
		<div class="row centered">
    		<iframe width="640" height="390" src="http://www.youtube.com/embed/4l2tLIZHlBQ?autoplay=1"></iframe>
		</div>
    </div><!-- /.container -->
    
    <div class="container">
    	<div class="row w centered">
    		<div class="col-lg-6 col-lg-offset-3">
    			<h3>Currently Parsing our "The Lightning Theif" - Approximately 80,000 words as Key/Value Pairs!</h3>
    		</div>
    	</div>
    </div>


    <!-- Bootstrap core JavaScript
    ================================================== -->
    <!-- Placed at the end of the document so the pages load faster -->
    <script src="https://code.jquery.com/jquery-1.10.2.min.js"></script>
    <script src="assets/js/bootstrap.min.js"></script>


    <script type="text/javascript" src="assets/js/jquery.backstretch.min.js"></script>
    <script type="text/javascript">$.backstretch("http://www.corydobson.com/LightSpeed/grease/assets/img/bg.jpg");</script>
  </body>
      </html>.toString()
    )
  )
  
  
  context.actorOf(Props[TransformationBackend], name = "backend")
  val greaseLines = Source.fromFile("greaseLightning.txt").mkString
  val greaseSplit = greaseLines.split("\\s+")
  val thunderLines = Source.fromFile("thunderstruck.txt").mkString
  val thunderSplit = thunderLines.split("\\s+")
  val thiefLines = Source.fromFile("thief.txt").mkString
  val thiefSplit = thiefLines.split("\\s+")
  
  
  def receive = {
    
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      sender ! index(backends.size)
      
    case HttpRequest(GET, Uri.Path("/lightspeed/test"), _, _, _) =>  
      	sender ! lightSpeedResponse(1,"")      
   
    case HttpRequest(GET, Uri.Path("/lightspeed/test/thunder"), _, _, _) =>  
      
      	 sender ! lightSpeedThunderResponse(1,"")      
      for(line <- thunderSplit){
            jobCounter += 1
    	    val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter, line)).mapTo[TransformationJob]
    		val originalSender = sender
      
      future onSuccess {
        case TransformationJob(count,text) =>
            	 originalSender ! lightSpeedThunderResponse(count,text)      
      		} 
      }
      
    case HttpRequest(GET, Uri.Path("/lightspeed/test/greaseLightning"), _, _, _) =>  
 
      	sender ! lightSpeedGreaseResponse(1,"")    
      	
      for(line <- greaseSplit){
            jobCounter += 1
    	    val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter, line)).mapTo[TransformationJob]
    		val originalSender = sender
      
      future onSuccess {
        case TransformationJob(count,text) =>
            	 originalSender ! lightSpeedGreaseResponse(count,text)      
      		} 
      }
      	
    case HttpRequest(GET, Uri.Path("/lightspeed/test/thief"), _, _, _) =>  
 
      	sender ! lightSpeedThiefResponse(1,"")    
      	
      for(line <- thiefSplit){
            jobCounter += 1
    	    val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter, line)).mapTo[TransformationJob]
    		val originalSender = sender
      
      future onSuccess {
        case TransformationJob(count,text) =>
            	 originalSender ! lightSpeedThiefResponse(count,text)      
      		} 
      }   	
      	/*
   case HttpRequest(GET, Uri.Path("/lightspeed/test/"), _, _, _) =>  
 
      	sender ! lightSpeedGreaseResponse(1,"")    
      	
      for(line <- greaseSplit){
            jobCounter += 1
    	    val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter, line)).mapTo[TransformationJob]
    		val originalSender = sender
      
      future onSuccess {
        case TransformationJob(count,text) =>
            	 originalSender ! lightSpeedGreaseResponse(count,text)      
      		} 
      } 
      */
      
    case BackendRegistration if !backends.contains(sender) =>
      context watch sender
      backends = backends :+ sender

    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
   
  }
}
//#frontend
