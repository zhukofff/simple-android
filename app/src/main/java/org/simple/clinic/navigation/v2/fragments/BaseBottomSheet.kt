package org.simple.clinic.navigation.v2.fragments

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.spotify.mobius.EventSource
import com.spotify.mobius.Init
import com.spotify.mobius.MobiusLoop
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import com.spotify.mobius.android.MobiusAndroid
import com.spotify.mobius.rx2.RxMobius
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import org.simple.clinic.mobius.ViewRenderer
import org.simple.clinic.mobius.eventSources
import org.simple.clinic.mobius.first
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.util.overrideCancellation
import org.simple.clinic.util.unsafeLazy

abstract class BaseBottomSheet<K : ScreenKey, B : ViewBinding, M : Parcelable, E, F> : BottomSheetDialogFragment() {

  companion object {
    private const val KEY_MODEL = "org.simple.clinic.navigation.v2.fragments.BaseScreen.KEY_MODEL"
  }

  private val loop: MobiusLoop.Builder<M, E, F> by unsafeLazy {
    RxMobius
        .loop(createUpdate()::update, createEffectHandler())
        .eventSources(additionalEventSources())
  }

  private val controller: MobiusLoop.Controller<M, E> by unsafeLazy {
    MobiusAndroid.controller(loop, defaultModel(), createInit())
  }

  protected val screenKey by unsafeLazy { ScreenKey.key<K>(this) }

  private var _binding: B? = null

  protected val binding: B
    get() = _binding!!

  private var behavior: BottomSheetBehavior<FrameLayout>? = null
  private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
    override fun onStateChanged(bottomSheet: View, newState: Int) {
      if (newState == BottomSheetBehavior.STATE_DRAGGING) {
        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
      }
    }

    override fun onSlide(bottomSheet: View, slideOffset: Float) {

    }
  }

  abstract fun defaultModel(): M

  abstract fun bindView(inflater: LayoutInflater, container: ViewGroup?): B

  open fun uiRenderer(): ViewRenderer<M> = NoopViewRenderer()

  open fun events(): Observable<E> = Observable.never()

  open fun createUpdate(): Update<M, E, F> = Update { _, _ -> noChange() }

  open fun createInit(): Init<M, F> = Init { model -> first(model) }

  open fun createEffectHandler(): ObservableTransformer<F, E> = ObservableTransformer { Observable.never() }

  open fun additionalEventSources(): List<EventSource<E>> = emptyList()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View? {
    _binding = bindView(inflater, container)

    return _binding?.root
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

    behavior = dialog.behavior
    behavior?.addBottomSheetCallback(bottomSheetCallback)

    // This is needed because the router is not aware of the changes
    // in the history when the bottom sheet dialog is dismissed in the
    // normal fashion.
    dialog.overrideCancellation(::backPressed)

    return dialog
  }

  private fun backPressed() {
    requireActivity().onBackPressed()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val rxBridge = RxMobiusBridge(events(), uiRenderer())
    controller.connect(rxBridge)

    if (savedInstanceState != null) {
      val savedModel = savedInstanceState.getParcelable<M>(KEY_MODEL)!!
      controller.replaceModel(savedModel)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    controller.disconnect()
    _binding = null
    behavior?.removeBottomSheetCallback(bottomSheetCallback)
    behavior = null
  }

  override fun onResume() {
    super.onResume()
    controller.start()
  }

  override fun onPause() {
    super.onPause()
    controller.stop()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putParcelable(KEY_MODEL, controller.model)
  }

  override fun dismiss() {
    backPressed()
    super.dismiss()
  }
}
