import { useEffect, useRef } from 'react';

import { Link } from 'react-router-dom';

import { getApiError } from '@/shared/api';

import { useLogoutMutation } from '../mutations';

import { ProfileAvatar } from './ProfileAvatar';

import type { UserResponse } from '../types';

interface ProfileDrawerProps {
  user: UserResponse;
  onClose: () => void;
}

const menuItemClass =
  'flex min-h-11 w-full items-center gap-2 rounded-xl border border-stone-200 bg-white px-4 text-sm font-semibold text-stone-700 transition hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600';

// 헤더 아바타에서 열리는 우측 프로필 드로어. 마운트되면 즉시 모달로 열리고
// ESC(onCancel)·오버레이 클릭·닫기 버튼으로 onClose를 호출해 언마운트된다.
// 앱이 가운데 480px 모바일 프레임이라 드로어는 뷰포트가 아닌 프레임 오른쪽 가장자리에 붙인다.
export function ProfileDrawer({ user, onClose }: ProfileDrawerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const logoutMutation = useLogoutMutation();

  useEffect(() => {
    const dialog = dialogRef.current;

    if (dialog && !dialog.open) {
      dialog.showModal();
    }

    return () => {
      if (dialog?.open) {
        dialog.close();
      }
    };
  }, []);

  return (
    <dialog
      ref={dialogRef}
      aria-label="프로필 메뉴"
      className="fixed inset-0 m-0 h-full max-h-none w-full max-w-none bg-transparent p-0 text-stone-900 backdrop:bg-black/40"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        className="mx-auto flex h-full w-full max-w-[480px] justify-end lg:mx-0 lg:ml-auto lg:max-w-none"
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            onClose();
          }
        }}
      >
        <section className="flex h-full w-72 max-w-[80vw] flex-col rounded-l-2xl bg-white p-5 shadow-xl lg:w-96 lg:max-w-none lg:px-7 lg:py-6">
          <button
            type="button"
            aria-label="프로필 메뉴 닫기"
            onClick={onClose}
            className="self-end text-2xl leading-none text-stone-500 transition hover:text-stone-800"
          >
            ×
          </button>

          <div className="mt-1 flex flex-col items-center">
            <ProfileAvatar nickname={user.nickname} className="h-16 w-16 text-3xl" />
            <p className="mt-3 font-bold">{user.nickname} 작가님 ✨</p>
          </div>

          <nav aria-label="프로필 바로가기" className="mt-6 space-y-2">
            <Link to="/archive" onClick={onClose} className={menuItemClass}>
              <span aria-hidden="true">🗄️</span> 내 서고
            </Link>

            <Link to="/my-page" onClick={onClose} className={menuItemClass}>
              <span aria-hidden="true">👤</span> 마이 페이지
            </Link>

            <button
              type="button"
              disabled={logoutMutation.isPending}
              onClick={() => logoutMutation.mutate(undefined, { onSuccess: onClose })}
              className={[menuItemClass, 'disabled:cursor-not-allowed disabled:opacity-50'].join(
                ' ',
              )}
            >
              <span aria-hidden="true">↪</span>
              {logoutMutation.isPending ? '로그아웃 중…' : '로그아웃'}
            </button>
          </nav>

          {logoutMutation.isError && (
            <p role="alert" className="mt-3 text-sm font-semibold text-red-700">
              {getApiError(logoutMutation.error).message || '로그아웃에 실패했습니다.'}
            </p>
          )}
        </section>
      </div>
    </dialog>
  );
}
