import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Stack, TextField, Typography } from '@mui/material';
import { useAuth } from './AuthContext';
import { isDevMode } from './devMode';

interface LoginState {
  from?: { pathname: string; search?: string; hash?: string };
  expired?: boolean;
}

export function LoginPage() {
  const { login, expired } = useAuth();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LoginState;
  const [employeeNo, setEmployeeNo] = useState('');
  const [password, setPassword] = useState('');
  const [failed, setFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setFailed(false);
    try {
      await login(employeeNo.trim(), password);
      const from = state.from;
      navigate(from ? `${from.pathname}${from.search ?? ''}${from.hash ?? ''}` : '/', { replace: true });
    } catch {
      setFailed(true);
      setSubmitting(false);
    }
  };

  return (
    <Stack component="form" spacing={2} onSubmit={submit} sx={{ maxWidth: 360, mx: 'auto' }}>
      <Typography variant="h5" component="h1">
        로그인
      </Typography>
      {(expired || state.expired) && <Alert severity="info">다시 로그인해 주세요.</Alert>}
      {failed && <Alert severity="error">사번 또는 비밀번호가 올바르지 않습니다.</Alert>}
      <TextField label="사번" value={employeeNo} onChange={(e) => setEmployeeNo(e.target.value)} autoFocus required />
      <TextField label="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      <Button type="submit" variant="contained" disabled={submitting}>
        로그인
      </Button>
      {isDevMode() && (
        <Typography variant="caption" color="text.secondary">
          개발용 계정: E1001·E1002·A0001 / dev-password
        </Typography>
      )}
    </Stack>
  );
}
