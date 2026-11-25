# Fragment

当前框架支持 Activity 托管的动态 Fragment。类型位于 `com.android.app`，不依赖
AndroidX。它采用 Android 风格的容器、事务和生命周期设计，回调签名适配本框架：
没有 Bundle 参数，也没有 start/stop 阶段。

## 使用

在 Activity 布局中放置容器：

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/content"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

Fragment 使用已有的资源 ID 和 LayoutInflater：

```java
import com.android.app.Fragment;
import com.android.view.LayoutInflater;
import com.android.view.View;
import com.android.view.ViewGroup;

public class HomeFragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(View view) {
        // 查找控件、设置事件监听器。
    }
}
```

Activity 在 setContentView 后提交事务：

```java
getFragmentManager().beginTransaction()
    .replace(R.id.content, new HomeFragment(), "home")
    .commit();
```

可通过 `findFragmentById(id)`、`findFragmentByTag(tag)` 和 `getFragments()` 查询。
同一个容器存在多个 Fragment 时，按最后添加的 Fragment 查找；标签不强制唯一。
`getFragments()` 返回不可修改的列表快照。

## 事务

- `add(id, fragment[, tag])`：添加到指定 ViewGroup 容器。
- `remove(fragment)`：移除该实例；未添加到本管理器的实例为空操作，其他管理器的实例报错。
- `replace(id, fragment[, tag])`：移除该容器中管理的所有 Fragment，再添加新实例；保留普通子 View。
- `commit()`：在宿主 UI 线程的 Looper 上排队执行，返回事务序号（不是返回栈索引）。
- `commitNow()`：立即执行当前事务，不提前执行其他已排队事务。
- `executePendingTransactions()`：同步执行所有待处理事务，返回是否执行过事务。

事务只能提交一次，提交后不可追加操作。同一实例不能重复添加或同时属于多个管理器。
移除后可以重新添加，此时重新执行创建生命周期。

管理器必须从创建 Activity 的线程获取，所有事务与查询也在该线程执行。
`commit()` 需要该线程已准备 Looper；无 Looper 的测试可使用 `commitNow()`。
回调中允许 `commit()` 排队；禁止在管理器回调期间调用 `commitNow()` 或同步排空事务。

每个事务先检查容器、实例归属及操作顺序，再更改界面；业务回调异常时清理正在添加的
Fragment。事务不提供回调异常时的整体回滚，已经执行的操作会保留，异常继续向外传播。
Activity 销毁后丢弃待执行事务，并拒绝新的事务。

## 生命周期

```text
onAttach(Context)
  → onCreate()
  → onCreateView(LayoutInflater, ViewGroup)
  → onViewCreated(View)
  → onResume()
  → onPause()
  → onDestroyView()
  → onDestroy()
  → onDetach()
```

- Activity 的 performResume/performPause/performDestroy 入口负责分发，应用不必在
  onResume/onPause/onDestroy 中调用 super 才能触发 Fragment 生命周期。
- 添加时根据宿主当前状态决定是否调用 onResume；窗口最小化和恢复同步暂停与恢复。
- onCreateView 返回的 View 必须没有父节点；管理器负责挂载。可以返回 null 创建无界面 Fragment，
  此时不调用 onViewCreated/onDestroyView。
- onViewCreated 时已挂载容器；onDestroyView 时已从父节点移除，getView 仍可用于清理。
  onDestroyView 结束后清空 View，onDetach 结束后清空宿主和管理器引用。
- Fragment 的 getActivity/getContext/getView 可返回 null；requireActivity/requireContext/requireView
  在当前状态不可用时抛出 IllegalStateException。
- Fragment 自己保存的 View、监听器或任务引用应在 onDestroyView/onDestroy 中清理。

ViewGroup.removeView 同时清理子树焦点、取消被移除子 View 的触摸捕获，并请求重新布局与绘制。

## 示例与验证

`packages/apps/fragment-demo` 提供两个页面来回替换的示例。构建：

```bash
bash packages/apps/build.sh fragment-demo
```

输出为 `out/system/app/fragment-demo/fragment-demo.apk`，完整构建后可从桌面打开 Fragment Demo。
示例通过 APK 中生成的资源索引加载布局。

运行无窗口测试（编译框架、打包示例、测试 APK 布局及按钮切换、检查窗口关闭回归）：

```bash
bash tests/run-fragment-tests.sh
```

## 当前边界

尚未实现返回栈、Bundle 参数和状态恢复、嵌套 Fragment、XML 静态声明、hide/show、
attach/detach 事务、动画或完整 Android/AndroidX API 兼容。当前 replace/remove 会同时销毁
Fragment 的 View 与对象生命周期；未来返回栈可扩展为保留对象、重新创建 View。
