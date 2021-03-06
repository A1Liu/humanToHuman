package main

import (
	"flag"
	"github.com/Dynamical-Systems-Laboratory/humanToHuman/database"
	"github.com/Dynamical-Systems-Laboratory/humanToHuman/utils"
	"github.com/Dynamical-Systems-Laboratory/humanToHuman/web"
	"github.com/gin-contrib/static"
	"github.com/gin-gonic/gin"
	"github.com/markbates/pkger"
	"golang.org/x/crypto/acme/autocert"
	"log"
	"net/http"
	"path"
	"strings"
)

type Dir pkger.Dir

func (d Dir) Open(name string) (http.File, error) {
	return pkger.Dir(d).Open(name)
}

func (d Dir) Exists(prefix, filepath string) bool {
	if p := strings.TrimPrefix(filepath, prefix); len(p) < len(filepath) {
		p = "/" + p
		file, err := d.Open(p)
		if err != nil {
			return false
		}
		defer file.Close()

		stats, err := file.Stat()
		if err != nil {
			return false
		}

		if stats.IsDir() {
			pDir := path.Join(p, "index.html")
			file, err := d.Open(pDir)
			if err != nil {
				return false
			}
			defer file.Close()

			_, err = file.Stat()
			if err != nil {
				return false
			}
		}

		return true
	}
	return false
}

func main() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	release := flag.Bool("release", false, "whether or not to run in release mode")
	password := flag.String("password", "", "password for the server")
	dbconnstr := flag.String("dbconnstr", "user=humantohuman password=humantohuman sslmode=disable host=localhost dbname=humantohuman", "database connection string")
	port := flag.String("port", "", "port of the server")
	domain := flag.String("domain", "", "domain of the server, to use for HTTPS")
	flag.Parse()

	if *release {
		if *password == "" {
			utils.Fail("password required for initial authentification!")
		}

		gin.SetMode(gin.ReleaseMode)
		web.Release = true
		var err error
		web.PasswordHash, err = utils.HashPassword(*password)
		utils.FailIf(err, "failed to hash provided password")
		*password = ""
	}

	router := gin.New()
	router.Use(gin.Logger())
	router.Use(gin.Recovery())
	router.Use(web.CORSMiddleware())

	database.ConnectToDb(*dbconnstr)

	router.POST("/clear", web.Clear)
	router.POST("/login", web.Login)
	router.POST("/addExperiment", web.NewExperiment)
	router.POST("/deleteExperiment", web.DeleteExperiment)

	router.GET("experiment/:experiment/exists", web.ExperimentExists)
	router.GET("/experiment/:experiment/devices.csv", web.GetDevicesCSV)
	router.GET("/experiment/:experiment/connections.csv", web.GetCSV)
	router.GET("/experiment/:experiment/policy", web.GetPrivacyPolicy)
	router.GET("/experiment/:experiment/description", web.GetDescription)
	router.POST("/experiment/:experiment/addUser", web.NewUser)
	router.POST("/experiment/:experiment/removeUser", web.RemoveUser)
	router.POST("/experiment/:experiment/addConnections", web.AddConnectionsUnsafe)
	router.POST("/experiment/:experiment/addConnectionsUnsafe", web.AddConnectionsUnsafe)
	dir := Dir(pkger.Dir("/templates"))
	router.Use(static.Serve("/", dir))

	if (*port != "" && *port != ":443") || *domain == "" {
		if *domain != "" {
			utils.Fail("can't do https encryption on port other than 443")
		}
		if *port == "" {
			*port = ":8080"
		}

		err := router.Run(*port)
		utils.FailIf(err, "why did this fail?")
	} else {
		err := http.Serve(autocert.NewListener(*domain), router)
		utils.FailIf(err, "why did this fail?")
	}
}
