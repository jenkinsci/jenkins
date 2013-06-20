package jenkins.plugins.ui_samples.CopyButton;

import groovy.swing.factory.TableFactory;
import lib.JenkinsTagLib
import lib.LayoutTagLib

def st=namespace("jelly:stapler")

t=namespace(JenkinsTagLib.class)
l=namespace(LayoutTagLib.class)

namespace("/lib/samples").sample(title:_("Copy Button")) {
  Map movieCast = my.getMovieCast();

  p(){
    h3() { text("Iterating over a map") }
  }

  raw("""<table class="stats">""")
  table() {
    tr() {
      th() { text("Actor") }
      th() { text("Character") }
    }
    movieCast.each{ k, v ->
      tr() {
        td() { text(k) }
        td() { text(v) }
      }
    }
  }

  List chars = my.getCharacters();

  p(){
    h3() { text("Iterating over a list") }

    table() {
      tr() {
        th() { text("Characters") }
      }
      chars.each{ v ->
        tr() {
          td() { text(v) }
        }
      }
    }
  }

  p(){
    h3() { text("Output random string: ${my.getRandomString()}") }
  }
}
