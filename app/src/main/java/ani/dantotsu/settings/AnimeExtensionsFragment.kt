package ani.dantotsu.settings

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.databinding.FragmentExtensionsBinding
import ani.dantotsu.settings.paging.AnimeExtensionAdapter
import ani.dantotsu.settings.paging.AnimeExtensionsViewModel
import ani.dantotsu.settings.paging.AnimeExtensionsViewModelFactory
import ani.dantotsu.settings.paging.OnAnimeInstallClickListener
import ani.dantotsu.snackString
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionsFragment : Fragment(),
    SearchQueryHandler, OnAnimeInstallClickListener {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AnimeExtensionsViewModel by viewModels {
        AnimeExtensionsViewModelFactory(animeExtensionManager)
    }

    private val adapter by lazy {
        AnimeExtensionAdapter(this)
    }

    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        binding.allExtensionsRecyclerView.isNestedScrollingEnabled = false
        binding.allExtensionsRecyclerView.adapter = adapter
        binding.allExtensionsRecyclerView.layoutManager = LinearLayoutManager(context)
        (binding.allExtensionsRecyclerView.layoutManager as LinearLayoutManager).isItemPrefetchEnabled =
            true

        lifecycleScope.launch {
            viewModel.pagerFlow.collectLatest {
                adapter.submitData(it)
            }
        }

        viewModel.invalidatePager() // Force a refresh of the pager

        return binding.root
    }

    override fun updateContentBasedOnQuery(query: String?) {
        viewModel.setSearchQuery(query ?: "")
    }

    override fun notifyDataChanged() {
        viewModel.invalidatePager()
    }

    override fun onInstallClick(pkg: AnimeExtension.Available) {
        val context = requireContext()
        if (isAdded) {
            val notificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Start the installation process
            animeExtensionManager.installExtension(pkg)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { installStep ->
                        val builder = NotificationCompat.Builder(
                            context,
                            Notifications.CHANNEL_DOWNLOADER_PROGRESS
                        )
                            .setSmallIcon(R.drawable.ic_round_sync_24)
                            .setContentTitle(getString(R.string.installing_extension))
                            .setContentText(getString(R.string.install_step, installStep))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                        notificationManager.notify(1, builder.build())
                    },
                    { error ->
                        Logger.log(error)
                        val builder = NotificationCompat.Builder(
                            context,
                            Notifications.CHANNEL_DOWNLOADER_ERROR
                        )
                            .setSmallIcon(R.drawable.ic_round_info_24)
                            .setContentTitle(getString(R.string.installation_failed, error.message))
                            .setContentText(getString(R.string.error_message, error.message))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                        notificationManager.notify(1, builder.build())
                        snackString(getString(R.string.installation_failed, error.message))
                    },
                    {
                        val builder = NotificationCompat.Builder(
                            context,
                            Notifications.CHANNEL_DOWNLOADER_PROGRESS
                        )
                            .setSmallIcon(R.drawable.ic_download_24)
                            .setContentTitle(getString(R.string.installation_complete))
                            .setContentText(getString(R.string.extension_has_been_installed))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                        notificationManager.notify(1, builder.build())
                        viewModel.invalidatePager()
                        snackString(getString(R.string.extension_installed))
                    }
                )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }


}