package webaugustus

class BootStrap {

    // create an instance of PredictionService at startup
    def predictionService
    // create an instance of TrainingService at startup
    def trainingService

    def init = { servletContext ->
    }
    def destroy = {
    }
}
