rootProject.name = "Non-stop"

includeBuild("../Kolor") {
    dependencySubstitution {
        substitute(module("io.github.Shai0717:Kolor:1.0.0")).using(project(":"))
    }
}
