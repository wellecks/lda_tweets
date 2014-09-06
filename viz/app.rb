require 'sinatra'

class App < Sinatra::Base

	set :static, true
	set :public_folder, File.dirname(__FILE__) + '/static'

	get '/' do
		File.read(File.join('static', 'index.html'))
	end
end
