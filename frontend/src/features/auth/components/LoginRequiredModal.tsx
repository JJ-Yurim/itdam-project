import { Link } from 'react-router-dom';

import { Modal } from '@/shared/components';

interface LoginRequiredModalProps {
  open: boolean;
  returnTo: string;
  onClose: () => void;
}

export function LoginRequiredModal({ open, returnTo, onClose }: LoginRequiredModalProps) {
  return (
    <Modal
      open={open}
      title="로그인이 필요한 기능이에요"
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            className="min-h-11 rounded-xl border border-stone-300 bg-white px-4 text-sm font-semibold text-stone-700 transition hover:bg-stone-50"
            onClick={onClose}
          >
            다음에 하기
          </button>
          <Link
            to="/login"
            state={{ from: returnTo }}
            className="flex min-h-11 items-center justify-center rounded-xl bg-violet-600 px-4 text-sm font-semibold text-white transition hover:bg-violet-700"
          >
            로그인하러 가기
          </Link>
        </>
      }
    >
      <p className="text-sm leading-6 text-stone-600">
        해당 기능은 로그인 후 이용할 수 있어요. 로그인하고 잇담의 이야기를 계속해 주세요.
      </p>
    </Modal>
  );
}
