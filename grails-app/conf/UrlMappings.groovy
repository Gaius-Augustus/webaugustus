class UrlMappings {
    
	static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "/about.gsp"(view: "/about")
        "/accuracy.gsp"(view: "/accuracy")
        "/datasets.gsp"(view: "/datasets")
        "/help.gsp"(view: "/help")
        "/index.gsp"(view: "/index")
        "/predictions_for_download.gsp"(view: "/predictions_for_download")
        "/predictiontutorial.gsp"(view: "/predictiontutorial")
        "/references.gsp"(view: "/references")
        "/trainingtutorial.gsp"(view: "/trainingtutorial")

        "/prediction"(controller:'Prediction', action:'create' )        
        "/training"(controller:'Training', action:'create' )
        
	}
}
