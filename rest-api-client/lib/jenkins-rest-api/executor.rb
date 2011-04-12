require 'rest_client'

module JenkinsRestAPI
  class BaseExecutor
    def execute!(args, options)
      configure(options)
      if args[0]
        send(options[:action], args[0])
      else
        send(options[:action])
      end
    end

    def configure(options)
      @server = "#{options[:server_url].chomp('/')}/rest-api"
    end
  end

  class JobExecutor < BaseExecutor
    def configure(options)
      super
      @job_url = "#{@server}/jobs"
    end

    def status(job)
      response = RestClient.get "#{@job_url}/#{job}"
      puts response.to_s
    end

    def delete(job)
      response = RestClient.delete "#{@job_url}/#{job}"
      puts response.to_s
    end

    def disable(job)
      response = RestClient.post "#{@job_url}/#{job}/disable", :accept => 'text/plain'
      puts response.to_s
    end

    def enable(job)
      response = RestClient.post "#{@job_url}/#{job}/enable", :accept => 'text/plain'
      puts response.to_s
    end

    def build(job)
      response = RestClient.post "#{@job_url}/#{job}/builds/kickoff", :accept => 'text/plain'
      puts response.to_s
    end

    def list 
      response = RestClient.get @job_url
      puts response.to_s
    end
  end

  class BuildExecutor < BaseExecutor
    def configure(options)
      super
      @job = options[:job]
      @build_url = "#{@server}/jobs/#{@job}/builds"
    end

    def status(build_number)
      response = RestClient.get "#{@build_url}/#{build_number}"
      puts response.to_s
    end

    def retain(build_number)
      response = RestClient.post "#{@build_url}/#{build_number}/retain", :accept => 'text/plain'
      puts response.to_s
    end

    def delete(build_number)
      response = RestClient.delete "#{@build_url}/#{build_number}"
      puts response.to_s
    end

    def logfile(build_number)
      response = RestClient.get "#{@build_url}/#{build_number}/logfile"
      puts response.to_s
    end

    def list
      response = RestClient.get @build_url
      puts response.to_s
    end
  end
end
