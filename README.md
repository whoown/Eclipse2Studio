# Eclipse2Studio
convert eclipse android project into android studio format. 
> 
[功能] 将Android在Eclipse下的工程，转换为AndroidStudio工程：
> 
> [使用]：
> 1. 确保本地已经正确配置过Java/Android开发环境。
> 2. 运行eclipse2studio.jar。可以直接运行脚本eclipse2studio.bat。
> 3. 根据提示，输入Eclipse工程路径，e.g: ~/Work/myapp/ or C:\Work\myapp
> 4. 转换完成后，会在原工程目录下，生成对应的AndroidStudio工程。e.g: ~/Work/myapp_studio

[注意]：
1. 如果本地没有配置有效Android-sdk环境变量者，需要手动指定工程的编译API-Level，以及Build-Tools的版本。配置在user_config.prop中。
2. 原工程的代码库依赖关系（library.reference），默认不会导入到Studio工程中。需要在将所有工程Import到AndroidStudio之后重新建立依赖关系。可以通过user_config强制要求导入库依赖关系，但不推荐。
3. 如果你在user_config.prop中提供的编译API-Level以及Build-Tools的版本不适用于当前开发环境的话。会默认优先使用当前开发环境中最新的Api-Target与build-tools版本。
4. 如果导入过程发生问题，在Studio的“Android”视图下无法查看工程时，可以在“Project”视图下找到导入后的工程。通过修改其build.gradle以解决问题。

