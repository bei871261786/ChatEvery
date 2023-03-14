package river.chat.chatevery.splash

import river.chat.businese_common.router.jump2Main
import river.chat.businese_main.home.HomeActivity
import river.chat.chatevery.databinding.ActivitySplashBinding
import river.chat.lib_core.view.main.BaseBindingViewModelActivity

/**
 * Created by beiyongChao on 2023/3/8
 * Description:
 */

class SplashActivity : BaseBindingViewModelActivity<ActivitySplashBinding, SplashViewModel>() {


    override fun initDataBinding(binding: ActivitySplashBinding) {
        super.initDataBinding(binding)
        HomeActivity.launch(this)
    }


    override fun createViewModel() = SplashViewModel()


}