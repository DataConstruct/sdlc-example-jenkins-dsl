require 'net/http'
require 'json'
require 'fileutils'
require "base64"
require 'yaml'
require 'erb'

class Templater
  include ERB::Util

  def initialize(app, repo)
    @app = app
    @repo = repo
  end

  def render
    template = ERB.new File.new(File.join(File.dirname(File.expand_path(__FILE__)), 'groovy.erb')).read, nil, '%'
    template.result(binding)
  end
end

groovy_output = File.join(File.dirname(File.expand_path(__FILE__)),'src', 'jobdsl', 'jobs.groovy')
uri = URI "https://api.github.com/search/code?q=path:/config%20user:DataConstruct%20extension:.yml&page=1"
req = Net::HTTP::Get.new(uri)
req.add_field('Authorization', "token #{ENV['GITHUB_TOKEN']}")
File.delete(groovy_output) if File.exist?(groovy_output)

res = Net::HTTP.start(uri.host, 443, use_ssl: true) do |http|
  http.request(req)
end

data = JSON.parse res.body

hacky_cache_dir = "#{ENV['HOME']}/.hacky"
unless File.directory?(hacky_cache_dir)
  FileUtils.mkdir_p(hacky_cache_dir)
end


data['items'].each do |item|
  repo_full_name = item['repository']['full_name']
  repo_name = item['repository']['name']
  config_sha = item['sha']
  config_cache_location = "#{hacky_cache_dir}/#{repo_full_name}/#{config_sha}"

  unless File.directory?(config_cache_location)
    FileUtils.mkdir_p(config_cache_location)
  end

  default_config = "#{config_cache_location}/default.yml"
  unless File.exist?(default_config)
    uri = URI "https://api.github.com/repos/#{repo_full_name}/contents/config/default.yml"
    req = Net::HTTP::Get.new(uri)
    req.add_field('Authorization', "token #{ENV['GITHUB_TOKEN']}")

    res = Net::HTTP.start(uri.host, 443, use_ssl: true) do |http|
      http.request(req)
    end
    data = JSON.parse res.body
    File.open(default_config, "w") do |file|
      file.puts  Base64.decode64(data['content'])
    end
  end
  repo_configs = YAML.load_file(default_config)

  app_name = repo_configs.key?('app') ? repo_configs['app'] : repo_name

  File.open(groovy_output, "a+") do |file|
    file.puts  Templater.new(app_name, repo_full_name).render
  end
end

File.open(groovy_output, "a+") do |file|
  file.puts  'AppDirectory.build(this)'
end