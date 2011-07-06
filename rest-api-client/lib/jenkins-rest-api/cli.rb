require 'choosy'
require 'jenkins-rest-api/executor'

module JenkinsRestAPI
  class CLI
    def execute!(args)
      command.execute!(args)
    end

    def command
      version_info = Choosy::Version.load_from_parent
      commands = [job_command, build_command, :help]

      Choosy::SuperCommand.new :jenkins do
        printer :manpage, :version => version_info.to_s,
                          :date => version_info.date,
                          :manual => "Jenkins CI"
        summary "Interacts with a Jenkins CI server via a RESTful interface."

        section "DESCRIPTION" do
          para "This tool interacts with the remote Jenkins CI server. It uses the RESTful API as currently deployed."
          para "It currently supports two mechanisms for interacting with the server, one about jobs and the other about builds."
        end

        section "COMMANDS" do
          commands.each do |cmd|
            command cmd
          end
        end

        section "GLOBAL OPTIONS" do
          string :server_url, "The remode Jenkins CI server base URL. This is required.", :required => true
          version version_info.to_s
          help
        end
      end
    end

    def job_command
      Choosy::Command.new :job do
        executor JobExecutor.new
        summary "Interacts with the jobs on the server."
        section "OPTIONS" do
          enum :action, [:status, :delete, :disable, :enable, :build, :list], "The action to perform on the remote server. The 'list' option is the default and the only action that doesn't require an additional argument. Other actions include: status, delete, disable, enable, and build." do
            default :list
          end
        end

        arguments do
          count 0..1
          metaname '[JOB_NAME]'
          validate do |args, options|
            if args.empty? && options[:action] != :list
              die "A job name is required for this action."
            end
          end
        end
      end
    end

    def build_command
      Choosy::Command.new :build do
        executor BuildExecutor.new
        summary "Interacts with the builds on the server."
        section "OPTIONS" do
          string :job, "The name of the associated job. This is required.", :required => true
          enum :action, [:status, :retain, :delete, :logfile, :list], "The action to to perform on the remote server. The 'list' option is the default and the only action that doesn't require an additional argument. Other actions include: status, retain, delete, and logfile." do
            default :list
          end
        end

        arguments do
          count 0..1
          metaname '[BUILD_NUMBER]'
          validate do |args, options|
            if args.empty? && options[:action] != :list
              die "A build number is required for this action."
            end
          end
        end
      end
    end#build_command
  end
end
