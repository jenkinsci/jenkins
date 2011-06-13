# -*- encoding: utf-8 -*-
require 'rubygems'
require 'rake'

Gem::Specification.new do |gem|
  gem.name           = 'jenkins-rest-api'
  gem.version        = begin
                         require 'choosy/version'
                         Choosy::Version.load_from_lib.to_s
                       rescue Exception
                         '0'
                       end
  gem.platform       = Gem::Platform::RUBY
  gem.executables    = %W{jenkins}

  gem.summary        = 'Simple command line utility for interacting remotely with a Jenkins CI server.'
  gem.description    = 'Calls the REST-ful API exposed by the Jenkins CI server.'
  gem.email          = ['madeonamac@gmail.com']
  gem.authors        = ['Gabe McArthur']
  gem.homepage       = 'http://github.com/jenkins-ci/jenkins'
  gem.files          = FileList["[A-Z]*", "{bin,lib,spec}/**/*"]
    
  gem.add_dependency    'choosy',     '>= 0.4.8'
  gem.add_dependency    'rest-client', '>= 1.6.1'

  gem.add_development_dependency 'rspec'
  gem.add_development_dependency 'mechanize'
  gem.add_development_dependency 'autotest'
  gem.add_development_dependency 'autotest-notification'
  gem.add_development_dependency 'ZenTest'

  gem.required_rubygems_version = ">= 1.3.6"
  gem.require_path = 'lib'
end
