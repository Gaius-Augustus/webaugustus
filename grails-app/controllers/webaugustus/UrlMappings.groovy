package webaugustus

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }
        
        "/"(view:'/index')
        "404"(view: '/404')
        "405"(view: '/405')
        "500"(view:'/error_size_limit', exception: org.apache.tomcat.util.http.fileupload.FileUploadBase$SizeLimitExceededException)
        "500"(view:'/error')
//        "/busy"(view: '/busy')
        "/error"(view:'/error')
        "/about"(view:'/about')
        "/accuracy"(view: '/accuracy')
        "/datasets"(view: '/datasets')
        "/help"(view: '/help')
        "/index"(view: '/index')
        "/predictiontutorial"(view: '/predictiontutorial')
        "/references"(view: '/references')
        "/trainingtutorial"(view: '/trainingtutorial')

        "/prediction"(controller:'Prediction', action:'create' )        
        "/training"(controller:'Training', action:'create' )

    }
}
